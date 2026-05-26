package com.e7.autoplatform.imagerecognition.home

import android.graphics.Bitmap
import com.e7.autoplatform.core.home.HomeDetectionResult
import com.e7.autoplatform.core.home.HomeStateDetector
import com.e7.autoplatform.core.home.HomeUiState
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.OffsetColor
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.PatternMatcher
import com.e7.autoplatform.core.image.RegionScanner
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ImageBasedHomeStateDetector(
    private val regionScanner: RegionScanner,
    private val patternMatcher: PatternMatcher,
    private val bitmapProvider: suspend () -> Bitmap,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : HomeStateDetector {

    private val rules: List<HomeRule> by lazy {
        json.decodeFromString<HomeRulePayload>(rulesJson).rules
            .mapNotNull { it.toHomeRule() }
    }

    override suspend fun detect(): HomeDetectionResult {
        val bitmap = bitmapProvider()

        for (rule in rules) {
            val result = when (rule) {
                is HomeRule.Single -> regionScanner.findColor(bitmap, rule.anchorColor, rule.tolerance, rule.region, step = 1)
                is HomeRule.Multi -> patternMatcher.findPattern(bitmap, rule.pattern, rule.tolerance, rule.region, step = 1)
            }
            if (result.matched) {
                return HomeDetectionResult(rule.state, confidence = result.score.coerceIn(0f, 1f))
            }
        }

        return HomeDetectionResult(HomeUiState.UNKNOWN, confidence = 0f)
    }

    private sealed class HomeRule(val state: HomeUiState) {
        class Single(
            state: HomeUiState,
            val region: ScanRegion,
            val anchorColor: Int,
            val tolerance: Int
        ) : HomeRule(state)

        class Multi(
            state: HomeUiState,
            val region: ScanRegion,
            val pattern: PatternDefinition,
            val tolerance: Int
        ) : HomeRule(state)
    }

    private fun HomeRuleEntry.toHomeRule(): HomeRule? {
        val mappedState = state.toHomeState() ?: return null
        val rg = ScanRegion(region.left, region.top, region.right, region.bottom)
        return when (type) {
            "single_color" -> {
                val anchor = anchor ?: return null
                HomeRule.Single(mappedState, rg, parseRgb(anchor.rgb), tolerance)
            }
            "multi_point" -> {
                val a = anchorRgb ?: return null
                val offsetValues = offsets?.map { OffsetColor(it.dx, it.dy, parseRgb(it.rgb)) } ?: emptyList()
                HomeRule.Multi(mappedState, rg, PatternDefinition(parseRgb(a), offsetValues), tolerance)
            }
            else -> null
        }
    }

    private fun parseRgb(hex: String): Int = (0xFF shl 24) or hex.removePrefix("#").toInt(16)

    private fun String.toHomeState(): HomeUiState? = when (this) {
        "HOME" -> HomeUiState.HOME
        "POPUP" -> HomeUiState.POPUP
        "LOGIN" -> HomeUiState.LOGIN
        "RESULT" -> HomeUiState.RESULT
        "UNKNOWN" -> HomeUiState.UNKNOWN
        else -> null
    }

    @Serializable
    private data class HomeRulePayload(val rules: List<HomeRuleEntry>)

    @Serializable
    private data class HomeRuleEntry(
        val id: String,
        val type: String,
        val state: String,
        val region: RegionJson,
        val tolerance: Int = 0,
        val anchor: AnchorJson? = null,
        val anchorRgb: String? = null,
        val offsets: List<OffsetJson>? = null
    )

    @Serializable
    private data class RegionJson(val left: Int, val top: Int, val right: Int, val bottom: Int)

    @Serializable
    private data class AnchorJson(val x: Int, val y: Int, val rgb: String)

    @Serializable
    private data class OffsetJson(val dx: Int, val dy: Int, val rgb: String)
}
