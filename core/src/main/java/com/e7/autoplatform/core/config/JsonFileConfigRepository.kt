package com.e7.autoplatform.core.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path

class JsonFileConfigRepository(
    private val baseDir: Path,
    private val securityPolicy: ConfigSecurityPolicy = DefaultConfigSecurityPolicy,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) : ConfigRepository {

    private val mutex = Mutex()

    override suspend fun <T> read(configFile: ConfigFile, serializer: KSerializer<T>, defaultValue: T): T {
        return mutex.withLock {
            val path = baseDir.resolve(configFile.fileName)
            val file = File(path.toUri())
            if (!file.exists()) return@withLock defaultValue

            val raw = file.readText(Charsets.UTF_8)
            when (val check = securityPolicy.validateRawJson(configFile, raw)) {
                is ConfigValidationResult.Invalid -> return@withLock defaultValue
                ConfigValidationResult.Valid -> Unit
            }

            runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(defaultValue)
        }
    }

    override suspend fun <T> write(configFile: ConfigFile, serializer: KSerializer<T>, value: T) {
        mutex.withLock {
            if (securityPolicy.allowNetworkSource) {
            throw IllegalStateException("Network config source is forbidden by default policy")
        }
            val baseDirectory = File(baseDir.toUri())
            baseDirectory.mkdirs()
            val path = baseDir.resolve(configFile.fileName)
            val raw = json.encodeToString(serializer, value)
            when (val check = securityPolicy.validateRawJson(configFile, raw)) {
                is ConfigValidationResult.Invalid -> throw IllegalArgumentException(check.reason)
                ConfigValidationResult.Valid -> Unit
            }
            File(path.toUri()).writeText(raw, Charsets.UTF_8)
        }
    }

    override suspend fun exists(configFile: ConfigFile): Boolean {
        return mutex.withLock {
            File(baseDir.resolve(configFile.fileName).toUri()).exists()
        }
    }
}
