package com.e7.autoplatform.core.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads image rules with deterministic priority:
 * curated rules first, full-migration rules as fallback.
 */
class RuleLoader(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(curatedRulesJson: String, fullRulesJson: String): RuleLoadResult {
        val issues = mutableListOf<RuleValidationIssue>()
        val curated = parse(curatedRulesJson, RuleSource.CURATED, issues)
        val full = parse(fullRulesJson, RuleSource.FULL, issues)

        val selectedById = LinkedHashMap<String, JsonObject>()
        val sourceById = LinkedHashMap<String, RuleSource>()
        val conflicts = mutableListOf<RuleConflict>()

        curated.rules.forEach { rule ->
            val id = rule.ruleIdOrNull() ?: return@forEach
            val existing = selectedById[id]
            if (existing == null) {
                selectedById[id] = rule
                sourceById[id] = RuleSource.CURATED
            } else if (existing != rule) {
                conflicts += RuleConflict(id = id, winner = RuleSource.CURATED, skipped = RuleSource.CURATED)
            }
        }

        full.rules.forEach { rule ->
            val id = rule.ruleIdOrNull() ?: return@forEach
            val existing = selectedById[id]
            if (existing == null) {
                selectedById[id] = rule
                sourceById[id] = RuleSource.FULL
            } else if (existing != rule) {
                conflicts += RuleConflict(id = id, winner = RuleSource.CURATED, skipped = RuleSource.FULL)
            }
        }

        return RuleLoadResult(
            task = curated.task ?: full.task,
            rules = selectedById.values.toList(),
            sources = sourceById.toMap(),
            conflicts = conflicts,
            issues = issues
        )
    }

    private fun parse(rawJson: String, source: RuleSource, issues: MutableList<RuleValidationIssue>): ParsedRules {
        return runCatching {
            val payload = json.decodeFromString(RulePayload.serializer(), rawJson)
            val validRules = payload.rules.filter { rule ->
                val id = rule.ruleIdOrNull()
                if (id.isNullOrBlank()) {
                    issues += RuleValidationIssue.MalformedRule(source, "Missing rule id")
                    false
                } else {
                    true
                }
            }
            ParsedRules(task = payload.task, rules = validRules)
        }.getOrElse { err ->
            issues += RuleValidationIssue.MalformedJson(source, err.message ?: "Invalid json")
            ParsedRules(task = null, rules = emptyList())
        }
    }

    private data class ParsedRules(val task: String?, val rules: List<JsonObject>)

    @Serializable
    private data class RulePayload(
        val task: String? = null,
        val rules: List<JsonObject> = emptyList()
    )
}

enum class RuleSource { CURATED, FULL }

data class RuleConflict(
    val id: String,
    val winner: RuleSource,
    val skipped: RuleSource
)

data class RuleLoadResult(
    val task: String?,
    val rules: List<JsonObject>,
    val sources: Map<String, RuleSource>,
    val conflicts: List<RuleConflict>,
    val issues: List<RuleValidationIssue> = emptyList()
)

sealed interface RuleValidationIssue {
    data class MalformedJson(val source: RuleSource, val reason: String) : RuleValidationIssue
    data class MalformedRule(val source: RuleSource, val reason: String) : RuleValidationIssue
}

private fun JsonObject.ruleIdOrNull(): String? {
    val primitive = this["id"] as? JsonPrimitive ?: return null
    return runCatching { primitive.content }.getOrNull()
}
