package cloud.angora.repository

import cloud.angora.Roles
import cloud.angora.Sessions
import cloud.angora.Users
import cloud.angora.constants.BackendConstants
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/** The result of resolving a session cookie: everything a request needs about its caller. */
data class ActiveSession(
    val sessionId: UUID,
    val userId: UUID,
    val companyId: UUID,
    val roleName: String
)

interface SessionRepository {
    fun create(
        userId: UUID,
        tokenHash: String,
        expiresAt: Instant,
        ipAddress: String?,
        userAgent: String?
    ): UUID

    /**
     * Resolves a token hash to its caller, or null if the session is unknown,
     * revoked, expired, or belongs to a user who is no longer active or has been
     * soft-deleted. The user checks are here rather than only at login because
     * suspending someone has to end the sessions they already hold.
     */
    fun findActiveByTokenHash(tokenHash: String): ActiveSession?

    fun revoke(sessionId: UUID, at: Instant)

    fun revokeAllForUser(userId: UUID, at: Instant): Int

    fun recordLastSeen(sessionId: UUID, at: Instant)

    /** Hard-deletes rows already past their expiry. Returns the number removed. */
    fun deleteExpired(now: Instant): Int
}

class SessionRepositoryImpl(private val database: Database) : SessionRepository {

    override fun create(
        userId: UUID,
        tokenHash: String,
        expiresAt: Instant,
        ipAddress: String?,
        userAgent: String?
    ): UUID {
        val now = Instant.now()
        return transaction(database) {
            Sessions.insertAndGetId {
                it[Sessions.userId] = userId
                it[Sessions.tokenHash] = tokenHash
                it[Sessions.expiresAt] = expiresAt
                it[Sessions.ipAddress] = ipAddress
                it[Sessions.userAgent] = userAgent
                it[createdAt] = now
                it[updatedAt] = now
            }.value
        }
    }

    override fun findActiveByTokenHash(tokenHash: String): ActiveSession? {
        val now = Instant.now()
        return transaction(database) {
            Sessions
                .innerJoin(Users, { Sessions.userId }, { Users.id })
                .innerJoin(Roles, { Users.roleId }, { Roles.id })
                .selectAll()
                .where {
                    (Sessions.tokenHash eq tokenHash) and
                        Sessions.revokedAt.isNull() and
                        (Sessions.expiresAt greater now) and
                        (Users.status eq BackendConstants.UserStatus.ACTIVE) and
                        Users.deletedAt.isNull()
                }
                .singleOrNull()
                ?.let { row ->
                    ActiveSession(
                        sessionId = row[Sessions.id].value,
                        userId = row[Users.id].value,
                        companyId = row[Users.companyId].value,
                        roleName = row[Roles.name]
                    )
                }
        }
    }

    override fun revoke(sessionId: UUID, at: Instant) {
        transaction(database) {
            Sessions.update({ (Sessions.id eq sessionId) and Sessions.revokedAt.isNull() }) {
                it[revokedAt] = at
            }
        }
    }

    override fun revokeAllForUser(userId: UUID, at: Instant): Int {
        return transaction(database) {
            Sessions.update({ (Sessions.userId eq userId) and Sessions.revokedAt.isNull() }) {
                it[revokedAt] = at
            }
        }
    }

    override fun recordLastSeen(sessionId: UUID, at: Instant) {
        transaction(database) {
            Sessions.update({ Sessions.id eq sessionId }) {
                it[lastSeenAt] = at
            }
        }
    }

    override fun deleteExpired(now: Instant): Int {
        return transaction(database) {
            Sessions.deleteWhere { Sessions.expiresAt less now }
        }
    }
}
