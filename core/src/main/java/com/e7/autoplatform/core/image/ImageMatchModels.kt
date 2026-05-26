package com.e7.autoplatform.core.image

data class ImagePoint(val x: Int, val y: Int)

data class MatchResult(
    val matched: Boolean,
    val point: ImagePoint? = null,
    val score: Float = 0f
)

data class ScanRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun clamp(width: Int, height: Int): ScanRegion {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        return ScanRegion(l, t, r, b)
    }

    companion object {
        fun full(width: Int, height: Int): ScanRegion = ScanRegion(0, 0, width, height)
    }
}

data class OffsetColor(val dx: Int, val dy: Int, val color: Int)

data class PatternDefinition(
    val anchorColor: Int,
    val offsets: List<OffsetColor>
)

data class TemplateDefinition(
    val id: String,
    val imagePath: String,
    val region: ScanRegion? = null,
    val step: Int = 1,
    val similarity: Float = 1f
)
