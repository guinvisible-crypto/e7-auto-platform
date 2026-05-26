package com.e7.autoplatform.core.config

import kotlinx.serialization.KSerializer

interface ConfigRepository {
    suspend fun <T> read(configFile: ConfigFile, serializer: KSerializer<T>, defaultValue: T): T
    suspend fun <T> write(configFile: ConfigFile, serializer: KSerializer<T>, value: T)
    suspend fun exists(configFile: ConfigFile): Boolean
}
