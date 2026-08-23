package cloud.angora.repository

import cloud.angora.ServiceTokens
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

data class ServiceTokenRecord(
    val id: UUID,
    val name: String
)

interface ServiceTokenRepository {
    /** Resolves a token hash to its owner, ignoring revoked rows. */
    fun findActiveByHash(tokenHash: String): ServiceTokenRecord?

    /**
     * Creates or updates the row for [name] with [tokenHash], clearing any prior
     * revocation. Used at startup to reconcile the configured token with the
     * database.
     */
    fun upsertByName(name: String, tokenHash: String)

    fun touchLastUsed(id: UUID, at: Instant)
}

class ServiceTokenRepositoryImpl(private val database: Database) : ServiceTokenRepository {

    override fun findActiveByHash(tokenHash: String): ServiceTokenRecord? {
        return transaction(database) {
            ServiceTokens.selectAll()
                .where { (ServiceTokens.tokenHash eq tokenHash) and ServiceTokens.revokedAt.isNull() }
                .singleOrNull()
                ?.let {
                    ServiceTokenRecord(
                        id = it[ServiceTokens.id].value,
                        name = it[ServiceTokens.name]
                    )
                }
        }
    }

    override fun upsertByName(name: String, tokenHash: String) {
        val now = Instant.now()
        transaction(database) {
            val existing = ServiceTokens.selectAll()
                .where { ServiceTokens.name eq name }
                .singleOrNull()

            if (existing == null) {
                ServiceTokens.insert {
                    it[ServiceTokens.name] = name
                    it[ServiceTokens.tokenHash] = tokenHash
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                ServiceTokens.update({ ServiceTokens.name eq name }) {
                    it[ServiceTokens.tokenHash] = tokenHash
                    it[revokedAt] = null
                }
            }
        }
    }

    override fun touchLastUsed(id: UUID, at: Instant) {
        transaction(database) {
            ServiceTokens.update({ ServiceTokens.id eq id }) {
                it[lastUsedAt] = at
            }
        }
    }
}
