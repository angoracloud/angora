package cloud.angora.repository

import cloud.angora.Roles
import cloud.angora.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * A user as the authentication path needs them, including the credential material
 * that [cloud.angora.dto.AuthUserResponse] deliberately omits. Never serialize
 * this type.
 *
 * Carries no failure counter: the lockout decision is made in SQL under a row
 * lock in [UserRepository.recordFailedLogin], not from a value read earlier.
 */
data class AuthUserRecord(
    val id: UUID,
    val companyId: UUID,
    val email: String,
    val fullName: String,
    val passwordHash: String,
    val status: String,
    val roleName: String,
    val lockedUntil: Instant?
)

interface UserRepository {
    /**
     * Looks up a non-soft-deleted user by email, case-insensitively. Returns them
     * whatever their status, so the caller can reject every reason identically.
     */
    fun findByEmail(email: String): AuthUserRecord?

    fun findById(userId: UUID): AuthUserRecord?

    /** Increments the failure counter, locking the account once it hits [lockThreshold]. */
    fun recordFailedLogin(userId: UUID, lockThreshold: Int, lockedUntil: Instant)

    /** Clears the failure counter and lock, and stamps `last_login_at`. */
    fun recordSuccessfulLogin(userId: UUID, at: Instant)
}

class UserRepositoryImpl(private val database: Database) : UserRepository {

    override fun findByEmail(email: String): AuthUserRecord? {
        return transaction(database) {
            // lower(email) matches the users_email_lower_key index from V7.
            usersWithRole()
                .where { (Users.email.lowerCase() eq email.lowercase()) and Users.deletedAt.isNull() }
                .singleOrNull()
                ?.toAuthUserRecord()
        }
    }

    override fun findById(userId: UUID): AuthUserRecord? {
        return transaction(database) {
            usersWithRole()
                .where { (Users.id eq userId) and Users.deletedAt.isNull() }
                .singleOrNull()
                ?.toAuthUserRecord()
        }
    }

    override fun recordFailedLogin(userId: UUID, lockThreshold: Int, lockedUntil: Instant) {
        transaction(database) {
            // forUpdate() locks the row for the rest of this transaction. Without
            // it, two simultaneous failed logins both read the same count and
            // both write count+1, so parallel guessing could stay under the
            // lockout threshold indefinitely.
            val current = Users.selectAll()
                .where { Users.id eq userId }
                .forUpdate()
                .singleOrNull()
                ?.get(Users.failedLoginAttempts) ?: return@transaction

            val attempts = (current + 1).toShort()
            Users.update({ Users.id eq userId }) {
                it[failedLoginAttempts] = attempts
                if (attempts >= lockThreshold) {
                    it[this.lockedUntil] = lockedUntil
                }
            }
        }
    }

    override fun recordSuccessfulLogin(userId: UUID, at: Instant) {
        transaction(database) {
            Users.update({ Users.id eq userId }) {
                it[failedLoginAttempts] = 0
                it[lockedUntil] = null
                it[lastLoginAt] = at
            }
        }
    }

    private fun usersWithRole() = Users.innerJoin(Roles, { Users.roleId }, { Roles.id }).selectAll()

    private fun ResultRow.toAuthUserRecord() = AuthUserRecord(
        id = this[Users.id].value,
        companyId = this[Users.companyId].value,
        email = this[Users.email],
        fullName = this[Users.fullName],
        passwordHash = this[Users.passwordHash],
        status = this[Users.status],
        roleName = this[Roles.name],
        lockedUntil = this[Users.lockedUntil]
    )
}
