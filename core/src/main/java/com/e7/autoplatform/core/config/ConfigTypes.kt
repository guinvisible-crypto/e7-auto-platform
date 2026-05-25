package com.e7.autoplatform.core.config

enum class ConfigFile(val fileName: String) {
    Config("config.json"),
    FightConfig("fightConfig.json"),
    BagConfig("bagConfig.json"),
    FunctionSetting("functionSetting.json"),
    AdvSetting("advSetting.json")
}

sealed class ConfigValidationResult {
    data object Valid : ConfigValidationResult()
    data class Invalid(val reason: String) : ConfigValidationResult()
}
