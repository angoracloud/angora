package cloud.angora.testsupport

import cloud.angora.constants.BackendConstants
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Base class for repository tests that need a real Postgres instance.
 *
 * Uses the Testcontainers "singleton container" pattern: the container and the
 * Exposed [Database] connection live in the companion object, so every
 * subclass shares the same one, started and migrated exactly once per JVM
 * rather than restarted per test class. It is deliberately never stopped —
 * Testcontainers' Ryuk sidecar reaps it at JVM shutdown. Do not add
 * @Testcontainers/@Container annotations here; those bind lifecycle to each
 * test class.
 */
abstract class PostgresRepositoryTest {

    protected val database: Database get() = Companion.database

    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("angora_test")
                .withUsername("angora_test")
                .withPassword("angora_test")
                .apply { start() }

        val database: Database by lazy {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()

            Database.connect(
                url = postgres.jdbcUrl,
                driver = BackendConstants.DatabaseDefaults.DRIVER_CLASS,
                user = postgres.username,
                password = postgres.password
            )
        }
    }
}
