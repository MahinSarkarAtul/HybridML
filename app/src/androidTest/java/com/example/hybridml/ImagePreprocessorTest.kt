package com.example.hybridml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hybridml.data.image.ImagePreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePreprocessorTest {

    @Test
    fun verifyDirectBufferCapacityAndShape() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val buffer = ImagePreprocessor.preprocess(bitmap)

        assertTrue("Output buffer must be direct native memory", buffer.isDirect)
        assertEquals("Capacity must equal 1 * 3 * 224 * 224 = 150,528 floats", 150528, buffer.capacity())
        assertEquals("Buffer position should be rewound to 0", 0, buffer.position())
        assertEquals("Buffer remaining should equal capacity", 150528, buffer.remaining())
    }

    @Test
    fun verifyNormalizationLimitsOnBlackBitmap() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val buffer = ImagePreprocessor.preprocess(bitmap)

        // For black (RGB = 0):
        // R: (0 - 0.485) / 0.229 = -2.1179f
        // G: (0 - 0.456) / 0.224 = -2.0357f
        // B: (0 - 0.406) / 0.225 = -1.8044f
        val rVal = buffer.get(0)
        val gVal = buffer.get(224 * 224)
        val bVal = buffer.get(2 * 224 * 224)

        assertEquals("Red channel black normalization error", -2.1179f, rVal, 0.05f)
        assertEquals("Green channel black normalization error", -2.0357f, gVal, 0.05f)
        assertEquals("Blue channel black normalization error", -1.8044f, bVal, 0.05f)
    }

    @Test
    fun verifyNormalizationLimitsOnWhiteBitmap() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val buffer = ImagePreprocessor.preprocess(bitmap)

        // For white (RGB = 255):
        // R: (1.0 - 0.485) / 0.229 = +2.2489f
        // G: (1.0 - 0.456) / 0.224 = +2.4286f
        // B: (1.0 - 0.406) / 0.225 = +2.6400f
        val rVal = buffer.get(0)
        val gVal = buffer.get(224 * 224)
        val bVal = buffer.get(2 * 224 * 224)

        assertEquals("Red channel white normalization error", 2.2489f, rVal, 0.05f)
        assertEquals("Green channel white normalization error", 2.4286f, gVal, 0.05f)
        assertEquals("Blue channel white normalization error", 2.6400f, bVal, 0.05f)
    }

    @Test
    fun verifyScalingAndCenterCroppingOnNonSquareBitmaps() {
        val landscapeBitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val landscapeBuffer = ImagePreprocessor.preprocess(landscapeBitmap)
        assertEquals(150528, landscapeBuffer.capacity())

        val portraitBitmap = Bitmap.createBitmap(150, 350, Bitmap.Config.ARGB_8888)
        val portraitBuffer = ImagePreprocessor.preprocess(portraitBitmap)
        assertEquals(150528, portraitBuffer.capacity())
    }
}
