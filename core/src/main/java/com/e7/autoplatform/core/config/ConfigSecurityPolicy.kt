package com.e7.autoplatform.core.config

interface ConfigSecurityPolicy {
    val allowNetworkSource: Boolean
    fun validateRawJson(configFile: ConfigFile, rawJson: String): ConfigValidationResult
}

object DefaultConfigSecurityPolicy : ConfigSecurityPolicy {
    override val allowNetworkSource: Boolean = false

    override fun validateRawJson(configFile: ConfigFile, rawJson: String): ConfigValidationResult {
        if (rawJson.isBlank()) return ConfigValidationResult.Invalid("json is blank")
        val trimmed = rawJson.trim()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return ConfigValidationResult.Invalid("json must start with '{' or '['")
        }
        return ConfigValidationResult.Valid
    }
}
