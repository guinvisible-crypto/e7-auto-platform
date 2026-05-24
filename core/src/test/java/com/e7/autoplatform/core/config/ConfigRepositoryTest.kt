package com.e7.autoplatform.core.config

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ConfigRepositoryTest {

    @Test
    fun `supports all five config files by name`() {
        assertEquals("config.json", ConfigFile.Config.fileName)
        assertEquals("fightConfig.json", ConfigFile.FightConfig.fileName)
        assertEquals("bagConfig.json", ConfigFile.BagConfig.fileName)
        assertEquals("functionSetting.json", ConfigFile.FunctionSetting.fileName)
        assertEquals("advSetting.json", ConfigFile.AdvSetting.fileName)
    }

    @Test
    fun `read returns default when file missing`() = runBlocking {
        val dir = Files.createTempDirectory("cfg-test-missing")
        val repo = JsonFileConfigRepository(dir)

        val defaultMap = mapOf("k" to "v")
        val result = repo.read(
            ConfigFile.Config,
            MapSerializer(String.serializer(), String.serializer()),
            defaultMap
        )

        assertEquals(defaultMap, result)
    }

    @Test
    fun `write and read typed config`() = runBlocking {
        val dir = Files.createTempDirectory("cfg-test-write")
        val repo = JsonFileConfigRepository(dir)

        val value = SampleConfig(retry = 5, enabled = true)
        repo.write(ConfigFile.AdvSetting, SampleConfig.serializer(), value)

        val restored = repo.read(ConfigFile.AdvSetting, SampleConfig.serializer(), SampleConfig())
        assertEquals(value, restored)
        assertTrue(repo.exists(ConfigFile.AdvSetting))
    }

    @Test
    fun `invalid json validation falls back to default`() = runBlocking {
        val dir = Files.createTempDirectory("cfg-test-invalid")
        Files.writeString(dir.resolve(ConfigFile.BagConfig.fileName), "not-json")
        val repo = JsonFileConfigRepository(dir)

        val default = SampleConfig(retry = 1, enabled = false)
        val restored = repo.read(ConfigFile.BagConfig, SampleConfig.serializer(), default)
        assertEquals(default, restored)
    }

    @Test
    fun `runtime state store is type safe with defaults`() = runBlocking {
        val state = InMemoryRuntimeStateStore()
        assertEquals(3, state.getInt("missing", 3))
        assertEquals("x", state.getString("missing", "x"))

        state.putInt("count", 9)
        state.putString("mode", "run")

        assertEquals(9, state.getInt("count", 0))
        assertEquals("run", state.getString("mode", ""))

        state.clear()
        assertFalse(state.getString("mode", "").isNotEmpty())
    }

    @Serializable
    data class SampleConfig(
        val retry: Int = 0,
        val enabled: Boolean = false
    )
}
