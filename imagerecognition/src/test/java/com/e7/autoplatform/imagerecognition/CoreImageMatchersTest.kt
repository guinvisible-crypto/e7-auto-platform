package com.e7.autoplatform.imagerecognition

import android.graphics.Bitmap
import com.e7.autoplatform.core.image.OffsetColor
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreImageMatchersTest {

    @Test
    fun `region scanner finds color with step and region`() {
        val bmp = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF000000.toInt())
        bmp.setPixel(4, 3, 0xFFFFFFFF.toInt())

        val matcher = CoreRegionScanner()
        val result = matcher.findColor(
            bitmap = bmp,
            targetColor = 0xFFFFFFFF.toInt(),
            tolerance = 0,
            region = ScanRegion(2, 2, 6, 6),
            step = 1
        )

        assertTrue(result.matched)
        assertEquals(4, result.point?.x)
        assertEquals(3, result.point?.y)
    }

    @Test
    fun `pattern matcher finds anchor and offsets`() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF000000.toInt())
        bmp.setPixel(2, 2, 0xFF112233.toInt())
        bmp.setPixel(3, 2, 0xFF223344.toInt())
        bmp.setPixel(2, 3, 0xFF334455.toInt())

        val pattern = PatternDefinition(
            anchorColor = 0xFF112233.toInt(),
            offsets = listOf(
                OffsetColor(1, 0, 0xFF223344.toInt()),
                OffsetColor(0, 1, 0xFF334455.toInt())
            )
        )

        val matcher = CorePatternMatcher()
        val result = matcher.findPattern(bmp, pattern, tolerance = 0, region = null, step = 1)

        assertTrue(result.matched)
        assertEquals(2, result.point?.x)
        assertEquals(2, result.point?.y)
    }

    @Test
    fun `template matcher is interface-level no business match yet`() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val matcher = CoreTemplateMatcher { null }
        val result = matcher.matchTemplate(
            bmp,
            com.e7.autoplatform.core.image.TemplateDefinition("id", "path"),
            threshold = 0.9f
        )
        assertFalse(result.matched)
    }
}
