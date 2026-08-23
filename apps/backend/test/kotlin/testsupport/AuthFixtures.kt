package cloud.angora.testsupport

import cloud.angora.Companies
import cloud.angora.Roles
import cloud.angora.Sessions
import cloud.angora.UserIdentities
import cloud.angora.Users
import cloud.angora.constants.BackendConstants
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * Fixture builders for the auth tables.
 *
 * A user can't exist without a company and a role, so every auth repository test
 * needs the same three-row setup; this keeps that in one place rather than
 * repeated per class. Cleanup deliberately stays each test's own responsibility,
 * per the repository-test convention — [cleanupAuthTables] is offered as the
 * correct delete order, not as automatic teardown.
 */
object AuthFixtures {

    /** Inserts a company and returns its id. */
    fun createCompany(database: Database, slug: String = "test-${UUID.randomUUID()}"): UUID {
        val now = Instant.now()
        return transaction(database) {
            Companies.insertAndGetId {
                it[name] = "Test Company"
                it[Companies.slug] = slug.take(100)
                it[createdAt] = now
                it[updatedAt] = now
            }.value
        }
    }

    /** Looks up one of the system roles seeded by the V2 migration. */
    fun systemRoleId(database: Database, name: String = BackendConstants.RoleNames.OWNER): UUID {
        return transaction(database) {
            Roles.selectAll()
                .where { Roles.name eq name }
                .first()[Roles.id]
                .value
        }
    }

    fun createUser(
        database: Database,
        companyId: UUID,
        email: String,
        passwordHash: String = "not-a-real-hash",
        status: String = BackendConstants.UserStatus.ACTIVE,
        roleName: String = BackendConstants.RoleNames.OWNER,
        deletedAt: Instant? = null,
        lockedUntil: Instant? = null,
        failedLoginAttempts: Short = 0
    ): UUID {
        val now = Instant.now()
        val role = systemRoleId(database, roleName)

        return transaction(database) {
            Users.insertAndGetId {
                it[Users.companyId] = companyId
                it[roleId] = role
                it[fullName] = "Test User"
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.status] = status
                it[Users.failedLoginAttempts] = failedLoginAttempts
                it[Users.lockedUntil] = lockedUntil
                it[Users.deletedAt] = deletedAt
                it[createdAt] = now
                it[updatedAt] = now
            }.value
        }
    }

    /**
     * Deletes the auth tables in foreign-key-safe order: rows that reference
     * users first, then users, then the company they belong to. System roles are
     * left alone — they come from a migration, not from a test.
     */
    fun cleanupAuthTables(database: Database) {
        transaction(database) {
            Sessions.deleteAll()
            UserIdentities.deleteAll()
            Users.deleteAll()
            Companies.deleteAll()
        }
    }
}
