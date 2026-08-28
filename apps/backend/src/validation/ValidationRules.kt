package cloud.angora.validation

import cloud.angora.constants.BackendConstants
import java.net.URI
import java.util.UUID

object ValidationRules {

    fun requireNonBlank(value: String?, fieldName: String, maxLength: Int? = null): String? {
        if (value.isNullOrBlank()) {
            return BackendConstants.Validation.Messages.mustNotBeBlank(fieldName)
        }
        if (maxLength != null && value.length > maxLength) {
            return BackendConstants.Validation.Messages.cannotExceedMaxLength(fieldName, maxLength)
        }
        return null
    }

    fun requireMaxLength(value: String?, fieldName: String, maxLength: Int): String? {
        if (value != null && value.length > maxLength) {
            return BackendConstants.Validation.Messages.cannotExceedMaxLength(fieldName, maxLength)
        }
        return null
    }

    fun requireMinLength(value: String?, fieldName: String, minLength: Int): String? {
        if (value != null && value.length < minLength) {
            return BackendConstants.Validation.Messages.mustBeAtLeastMinLength(fieldName, minLength)
        }
        return null
    }

    fun requireEmail(value: String?, fieldName: String = BackendConstants.Validation.Fields.EMAIL): String? {
        if (value.isNullOrBlank()) {
            return BackendConstants.Validation.Messages.mustNotBeBlank(fieldName)
        }
        if (!BackendConstants.Validation.Patterns.EMAIL_REGEX.matcher(value).matches()) {
            return BackendConstants.Validation.Messages.mustBeValidEmail(fieldName)
        }
        if (value.length > BackendConstants.Validation.Limits.MAX_EMAIL_LENGTH) {
            return BackendConstants.Validation.Messages.cannotExceedMaxLength(
                fieldName,
                BackendConstants.Validation.Limits.MAX_EMAIL_LENGTH
            )
        }
        return null
    }

    fun requireUuid(value: String?, fieldName: String = BackendConstants.Validation.Fields.ID): String? {
        if (value.isNullOrBlank()) {
            return BackendConstants.Validation.Messages.mustNotBeBlank(fieldName)
        }
        return try {
            UUID.fromString(value)
            null
        } catch (_: IllegalArgumentException) {
            BackendConstants.Validation.Messages.mustBeValidUuid(fieldName)
        }
    }

    fun requirePositiveOrZero(value: Int, fieldName: String): String? {
        if (value < BackendConstants.Validation.Limits.MIN_MEMBER_COUNT) {
            return BackendConstants.Validation.Messages.mustBeZeroOrPositive(fieldName)
        }
        return null
    }

    fun requireRange(value: Int, fieldName: String, min: Int, max: Int): String? {
        if (value < min || value > max) {
            return BackendConstants.Validation.Messages.mustBeBetween(fieldName, min, max)
        }
        return null
    }

    fun requireUrl(value: String?, fieldName: String, maxLength: Int? = null): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        if (maxLength != null && value.length > maxLength) {
            return BackendConstants.Validation.Messages.cannotExceedMaxLength(fieldName, maxLength)
        }
        return try {
            val uri = URI.create(value)
            if (uri.scheme == null || !BackendConstants.Validation.Patterns.ALLOWED_URL_SCHEMES.contains(uri.scheme.lowercase())) {
                BackendConstants.Validation.Messages.mustBeValidHttpOrHttpsUrl(fieldName)
            } else {
                null
            }
        } catch (_: Exception) {
            BackendConstants.Validation.Messages.mustBeValidUrl(fieldName)
        }
    }
}
