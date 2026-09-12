package com.example.hybridml.data.image

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.round

object ImagePreprocessor {

    const val INPUT_CHANNELS = 3
    const val INPUT_WIDTH = 224
    const val INPUT_HEIGHT = 224
    const val TARGET_SHORT_EDGE = 256

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * Preprocesses an Android [Bitmap] into an NCHW [FloatBuffer] of shape [1, 3, 224, 224].
     *
     * Pipeline:
     * 1. Bilinear-scale shortest edge to 256 with explicit rounding.
     * 2. Center-crop to 224x224.
     * 3. Normalize via ImageNet mean/std into native-order direct FloatBuffer in NCHW layout.
     */
    fun preprocess(bitmap: Bitmap): FloatBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val shortestEdge = minOf(width, height)

        val scale = TARGET_SHORT_EDGE.toFloat() / shortestEdge.toFloat()
        val scaledWidth = round(width * scale).toInt()
        val scaledHeight = round(height * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val cropX = (scaledWidth - INPUT_WIDTH) / 2
        val cropY = (scaledHeight - INPUT_HEIGHT) / 2
        val croppedBitmap = Bitmap.createBitmap(scaledBitmap, cropX, cropY, INPUT_WIDTH, INPUT_HEIGHT)

        val area = INPUT_WIDTH * INPUT_HEIGHT
        val totalFloats = 1 * INPUT_CHANNELS * area
        val byteBuffer = ByteBuffer.allocateDirect(totalFloats * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        val floatBuffer = byteBuffer.asFloatBuffer()

        val pixels = IntArray(area)
        croppedBitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

        val meanR = MEAN[0]
        val meanG = MEAN[1]
        val meanB = MEAN[2]
        val stdR = STD[0]
        val stdG = STD[1]
        val stdB = STD[2]

        val rOffset = 0
        val gOffset = area
        val bOffset = 2 * area

        for (i in 0 until area) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatBuffer.put(rOffset + i, (r - meanR) / stdR)
            floatBuffer.put(gOffset + i, (g - meanG) / stdG)
            floatBuffer.put(bOffset + i, (b - meanB) / stdB)
        }

        floatBuffer.rewind()

        if (scaledBitmap != bitmap && scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }
        if (croppedBitmap != bitmap) {
            croppedBitmap.recycle()
        }

        return floatBuffer
    }
}
