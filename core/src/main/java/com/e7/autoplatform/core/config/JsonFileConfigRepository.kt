package com.e7.autoplatform.core.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class JsonFileConfigRepository(
    private val baseDir: Path,
    private val securityPolicy: ConfigSecurityPolicy = DefaultConfigSecurityPolicy,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) : ConfigRepository {

    private val mutex = Mutex()

    override suspend fun <T> read(configFile: ConfigFile, serializer: KSerializer<T>, defaultValue: T): T = mutex.withLock {
        val path = baseDir.resolve(configFile.fileName)
        if (!Files.exists(path)) return defaultValue

        val raw = Files.readString(path, StandardCharsets.UTF_8)
        when (val check = securityPolicy.validateRawJson(configFile, raw)) {
            is ConfigValidationResult.Invalid -> return defaultValue
            ConfigValidationResult.Valid -> Unit
        }

        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(defaultValue)
    }

    override suspend fun <T> write(configFile: ConfigFile, serializer: KSerializer<T>, value: T) = mutex.withLock {
        if (securityPolicy.allowNetworkSource) {
            throw IllegalStateException("Network config source is forbidden by default policy")
        }
        Files.createDirectories(baseDir)
        val path = baseDir.resolve(configFile.fileName)
        val raw = json.encodeToString(serializer, value)
        when (val check = securityPolicy.validateRawJson(configFile, raw)) {
            is ConfigValidationResult.Invalid -> throw IllegalArgumentException(check.reason)
            ConfigValidationResult.Valid -> Unit
        }
        Files.writeString(path, raw, StandardCharsets.UTF_8)
    }

    override suspend fun exists(configFile: ConfigFile): Boolean = mutex.withLock {
        Files.exists(baseDir.resolve(configFile.fileName))
    }
}
