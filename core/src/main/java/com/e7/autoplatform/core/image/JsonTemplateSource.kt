package com.e7.autoplatform.core.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JsonTemplateSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun parse(rawJson: String): List<TemplateDefinition> {
        val payload = json.decodeFromString(TemplatePayload.serializer(), rawJson)
        return payload.templates.map {
            TemplateDefinition(
                id = it.id,
                imagePath = it.imagePath,
                region = it.region?.let { r -> ScanRegion(r.left, r.top, r.right, r.bottom) },
                step = it.step,
                similarity = it.similarity
            )
        }
    }

    @Serializable
    private data class TemplatePayload(val templates: List<TemplateEntry>)

    @Serializable
    private data class TemplateEntry(
        val id: String,
        val imagePath: String,
        val region: RegionEntry? = null,
        val step: Int = 1,
        val similarity: Float = 1f
    )

    @Serializable
    private data class RegionEntry(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
