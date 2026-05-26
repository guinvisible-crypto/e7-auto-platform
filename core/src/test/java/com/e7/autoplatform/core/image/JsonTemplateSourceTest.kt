package com.e7.autoplatform.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JsonTemplateSourceTest {
    @Test
    fun `parse template definitions from json`() {
        val raw = """
            {
              "templates": [
                {
                  "id": "home_btn",
                  "imagePath": "templates/home.png",
                  "region": {"left": 1, "top": 2, "right": 100, "bottom": 200},
                  "step": 2,
                  "similarity": 0.95
                }
              ]
            }
        """.trimIndent()

        val result = JsonTemplateSource().parse(raw)
        assertEquals(1, result.size)
        assertEquals("home_btn", result[0].id)
        assertEquals("templates/home.png", result[0].imagePath)
        assertEquals(2, result[0].step)
        assertEquals(0.95f, result[0].similarity)
        assertNotNull(result[0].region)
    }
}
