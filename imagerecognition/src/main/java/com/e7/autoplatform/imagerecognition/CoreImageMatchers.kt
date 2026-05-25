package com.e7.autoplatform.imagerecognition

import android.graphics.Bitmap
import com.e7.autoplatform.core.image.ImagePoint
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.OffsetColor
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.PatternMatcher
import com.e7.autoplatform.core.image.RegionScanner
import com.e7.autoplatform.core.image.ScanRegion
import com.e7.autoplatform.core.image.TemplateDefinition
import com.e7.autoplatform.core.image.TemplateMatcher

class CoreRegionScanner : RegionScanner {
    override fun findColor(bitmap: Bitmap, targetColor: Int, tolerance: Int, region: ScanRegion?, step: Int): MatchResult {
        val safeStep = step.coerceAtLeast(1)
        val scanRegion = (region ?: ScanRegion.full(bitmap.width, bitmap.height)).clamp(bitmap.width, bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var y = scanRegion.top
        while (y < scanRegion.bottom) {
            var idx = y * bitmap.width + scanRegion.left
            var x = scanRegion.left
            while (x < scanRegion.right) {
                if (withinTolerance(pixels[idx], targetColor, tolerance)) {
                    return MatchResult(true, ImagePoint(x, y), 1f)
                }
                x += safeStep
                idx += safeStep
            }
            y += safeStep
        }
        return MatchResult(false)
    }
}

class CorePatternMatcher : PatternMatcher {
    override fun findPattern(bitmap: Bitmap, pattern: PatternDefinition, tolerance: Int, region: ScanRegion?, step: Int): MatchResult {
        val safeStep = step.coerceAtLeast(1)
        val scanRegion = (region ?: ScanRegion.full(bitmap.width, bitmap.height)).clamp(bitmap.width, bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var y = scanRegion.top
        while (y < scanRegion.bottom) {
            var idx = y * bitmap.width + scanRegion.left
            var x = scanRegion.left
            while (x < scanRegion.right) {
                if (withinTolerance(pixels[idx], pattern.anchorColor, tolerance) &&
                    allOffsetsMatch(pixels, bitmap.width, bitmap.height, x, y, pattern.offsets, tolerance)
                ) {
                    return MatchResult(true, ImagePoint(x, y), 1f)
                }
                x += safeStep
                idx += safeStep
            }
            y += safeStep
        }
        return MatchResult(false)
    }

    private fun allOffsetsMatch(
        pixels: IntArray,
        width: Int,
        height: Int,
        baseX: Int,
        baseY: Int,
        offsets: List<OffsetColor>,
        tolerance: Int
    ): Boolean {
        for (o in offsets) {
            val x = baseX + o.dx
            val y = baseY + o.dy
            if (x < 0 || y < 0 || x >= width || y >= height) return false
            if (!withinTolerance(pixels[y * width + x], o.color, tolerance)) return false
        }
        return true
    }
}

class CoreTemplateMatcher(
    private val templateBitmapProvider: (String) -> Bitmap?
) : TemplateMatcher {
    override fun matchTemplate(bitmap: Bitmap, template: TemplateDefinition, threshold: Float): MatchResult {
        val tpl = templateBitmapProvider(template.imagePath) ?: return MatchResult(false)
        if (tpl.width <= 0 || tpl.height <= 0 || tpl.width > bitmap.width || tpl.height > bitmap.height) return MatchResult(false)

        val searchRegion = (template.region ?: ScanRegion.full(bitmap.width, bitmap.height)).clamp(bitmap.width, bitmap.height)
        val safeStep = template.step.coerceAtLeast(1)
        val effectiveThreshold = threshold.coerceIn(0f, 1f)

        val srcPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val tplPixels = IntArray(tpl.width * tpl.height)
        tpl.getPixels(tplPixels, 0, tpl.width, 0, 0, tpl.width, tpl.height)

        var bestScore = 0f
        var bestPoint: ImagePoint? = null

        val maxX = searchRegion.right - tpl.width
        val maxY = searchRegion.bottom - tpl.height
        var y = searchRegion.top
        while (y <= maxY) {
            var x = searchRegion.left
            while (x <= maxX) {
                val score = similarityAt(srcPixels, bitmap.width, tplPixels, tpl.width, tpl.height, x, y)
                if (score > bestScore) {
                    bestScore = score
                    bestPoint = ImagePoint(x, y)
                    if (bestScore >= effectiveThreshold) {
                        return MatchResult(true, bestPoint, bestScore)
                    }
                }
                x += safeStep
            }
            y += safeStep
        }

        return MatchResult(bestScore >= effectiveThreshold, bestPoint, bestScore)
    }

    private fun similarityAt(
        src: IntArray,
        srcWidth: Int,
        tpl: IntArray,
        tplWidth: Int,
        tplHeight: Int,
        offsetX: Int,
        offsetY: Int
    ): Float {
        var score = 0f
        val total = tpl.size * 3f
        var ty = 0
        while (ty < tplHeight) {
            var tx = 0
            while (tx < tplWidth) {
                val s = src[(offsetY + ty) * srcWidth + (offsetX + tx)]
                val t = tpl[ty * tplWidth + tx]
                val dr = kotlin.math.abs(((s shr 16) and 0xFF) - ((t shr 16) and 0xFF))
                val dg = kotlin.math.abs(((s shr 8) and 0xFF) - ((t shr 8) and 0xFF))
                val db = kotlin.math.abs((s and 0xFF) - (t and 0xFF))
                score += (255 - dr) + (255 - dg) + (255 - db)
                tx++
            }
            ty++
        }
        return score / total / 255f
    }
}

private fun withinTolerance(color: Int, target: Int, tolerance: Int): Boolean {
    val dr = ((color shr 16) and 0xFF) - ((target shr 16) and 0xFF)
    val dg = ((color shr 8) and 0xFF) - ((target shr 8) and 0xFF)
    val db = (color and 0xFF) - (target and 0xFF)
    return kotlin.math.abs(dr) <= tolerance && kotlin.math.abs(dg) <= tolerance && kotlin.math.abs(db) <= tolerance
}
