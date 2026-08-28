package cloud.angora

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.timestamp

object Companies : UUIDTable("companies") {
    val name = varchar("name", 255)
    val slug = varchar("slug", 100).uniqueIndex()
    val status = varchar("status", 20).default("active")
    val timezone = varchar("timezone", 64).default("UTC")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Roles : UUIDTable("roles") {
    val companyId = reference("company_id", Companies).nullable()
    val name = varchar("name", 50)
    val description = text("description").nullable()
    val isSystem = bool("is_system").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Users : UUIDTable("users") {
    val companyId = reference("company_id", Companies)
    val roleId = reference("role_id", Roles)
    val fullName = varchar("full_name", 255)
    val email = varchar("email", 320)
    val passwordHash = text("password_hash")
    val status = varchar("status", 20).default("invited")
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    val twoFactorEnabled = bool("two_factor_enabled").default(false)
    val twoFactorSecret = text("two_factor_secret").nullable()
    val lastLoginAt = timestamp("last_login_at").nullable()
    val failedLoginAttempts = short("failed_login_attempts").default(0)
    val lockedUntil = timestamp("locked_until").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
}

object Accounts : UUIDTable("accounts") {
    val companyId = reference("company_id", Companies)
    val name = varchar("name", 255)
    val domain = varchar("domain", 255).nullable()
    val industry = varchar("industry", 100).nullable()
    val status = varchar("status", 20).default("prospect")
    val ownerId = reference("owner_id", Users).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Contacts : UUIDTable("contacts") {
    val companyId = reference("company_id", Companies)
    val accountId = reference("account_id", Accounts).nullable()
    val userId = reference("user_id", Users).nullable().uniqueIndex()
    val fullName = varchar("full_name", 255)
    val email = varchar("email", 320).nullable()
    val phone = varchar("phone", 50).nullable()
    val jobTitle = varchar("job_title", 150).nullable()
    val lifecycleStage = varchar("lifecycle_stage", 20).default("lead")
    val ownerId = reference("owner_id", Users).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object DiscordServers : UUIDTable("discord_servers") {
    val guildId = varchar("guild_id", 64).uniqueIndex()
    val name = varchar("name", 255)
    val iconUrl = varchar("icon_url", 512).nullable()
    val ownerId = varchar("owner_id", 64).nullable()
    val memberCount = integer("member_count").default(0)
    val botJoined = bool("bot_joined").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
}

