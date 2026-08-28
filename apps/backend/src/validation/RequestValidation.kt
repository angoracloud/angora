package cloud.angora.validation

import cloud.angora.constants.BackendConstants
import cloud.angora.dto.SyncGuildRequest
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun validateSyncGuildRequest(req: SyncGuildRequest): ValidationResult {
    val reasons = mutableListOf<String>()

    ValidationRules.requireNonBlank(
        req.guildId,
        BackendConstants.Validation.Fields.GUILD_ID,
        maxLength = BackendConstants.Validation.Limits.MAX_GUILD_ID_LENGTH
    )?.let { reasons.add(it) }

    ValidationRules.requireNonBlank(
        req.name,
        BackendConstants.Validation.Fields.NAME,
        maxLength = BackendConstants.Validation.Limits.MAX_NAME_LENGTH
    )?.let { reasons.add(it) }

    ValidationRules.requireUrl(
        req.iconUrl,
        BackendConstants.Validation.Fields.ICON_URL,
        maxLength = BackendConstants.Validation.Limits.MAX_ICON_URL_LENGTH
    )?.let { reasons.add(it) }

    ValidationRules.requireMaxLength(
        req.ownerId,
        BackendConstants.Validation.Fields.OWNER_ID,
        maxLength = BackendConstants.Validation.Limits.MAX_OWNER_ID_LENGTH
    )?.let { reasons.add(it) }

    ValidationRules.requirePositiveOrZero(
        req.memberCount,
        BackendConstants.Validation.Fields.MEMBER_COUNT
    )?.let { reasons.add(it) }

    return if (reasons.isEmpty()) {
        ValidationResult.Valid
    } else {
        ValidationResult.Invalid(reasons)
    }
}

fun RequestValidationConfig.configureRequestValidation() {
    validate<SyncGuildRequest> { req ->
        validateSyncGuildRequest(req)
    }
}
