package com.e7.autoplatform.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLoaderTest {
    @Test
    fun `curated rules override full rules and full acts as fallback`() {
        val curated = """
            {
              "task": "arena",
              "rules": [
                {"id": "r1", "type": "single_color", "tolerance": 10},
                {"id": "r2", "type": "multi_point", "tolerance": 20}
              ]
            }
        """.trimIndent()
        val full = """
            {
              "task": "arena",
              "rules": [
                {"id": "r1", "type": "single_color", "tolerance": 99},
                {"id": "r3", "type": "single_color", "tolerance": 30}
              ]
            }
        """.trimIndent()

        val result = RuleLoader().load(curated, full)

        assertEquals(3, result.rules.size)
        assertEquals(RuleSource.CURATED, result.sources["r1"])
        assertEquals(RuleSource.CURATED, result.sources["r2"])
        assertEquals(RuleSource.FULL, result.sources["r3"])
        assertEquals(1, result.conflicts.size)
        assertEquals("r1", result.conflicts.first().id)
    }

    @Test
    fun `deterministic selection avoids duplicate matching ids`() {
        val curated = """
            {"rules": [{"id": "a"}, {"id": "b"}]}
        """.trimIndent()
        val full = """
            {"rules": [{"id": "b"}, {"id": "c"}, {"id": "a"}]}
        """.trimIndent()

        val result = RuleLoader().load(curated, full)
        val ids = result.rules.mapNotNull { it["id"]?.toString()?.replace("\"", "") }

        assertEquals(listOf("a", "b", "c"), ids)
        assertTrue(ids.distinct().size == ids.size)
    }

    @Test
    fun `invalid json is reported and valid side still loads`() {
        val curated = """{"rules":[{"id":"a"}]}"""
        val full = """{"rules":[{"id":"b"}"""

        val result = RuleLoader().load(curated, full)

        val ids = result.rules.mapNotNull { it["id"]?.toString()?.replace("\"", "") }
        assertEquals(listOf("a"), ids)
        assertTrue(result.issues.any { it is RuleValidationIssue.MalformedJson && it.source == RuleSource.FULL })
    }

    @Test
    fun `malformed rule entries are reported and skipped`() {
        val curated = """{"rules":[{"type":"single_color"},{"id":"ok"}]}"""
        val full = """{"rules":[]}"""

        val result = RuleLoader().load(curated, full)
        val ids = result.rules.mapNotNull { it["id"]?.toString()?.replace("\"", "") }

        assertEquals(listOf("ok"), ids)
        assertTrue(result.issues.any { it is RuleValidationIssue.MalformedRule && it.source == RuleSource.CURATED })
    }
}
