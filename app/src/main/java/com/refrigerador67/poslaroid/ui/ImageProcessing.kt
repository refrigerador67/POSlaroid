package com.refrigerador67.poslaroid.ui

import android.graphics.Bitmap
import android.graphics.Color

fun toGrayscale(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val grayPixels = IntArray(width * height)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val gray = (0.3 * red + 0.59 * green + 0.11 * blue).toInt()
        val grayPixel = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        grayPixels[i] = grayPixel
    }

    val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    grayBitmap.setPixels(grayPixels, 0, width, 0, 0, width, height)

    return grayBitmap
}

fun floydSteinbergDithering(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val ditheredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val errorDiffusionMatrix = arrayOf(
        intArrayOf(0, 0, 0, 7),
        intArrayOf(3, 5, 1, 0)
    )

    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val oldPixel = pixels[index]
            val oldGray = Color.red(oldPixel)
            val newGray = if (oldGray > 128) 255 else 0
            val error = oldGray - newGray

            pixels[index] = Color.rgb(newGray, newGray, newGray)

            for (dy in errorDiffusionMatrix.indices) {
                for (dx in errorDiffusionMatrix[dy].indices) {
                    val newX = x + dx - 1
                    val newY = y + dy
                    if (newX < 0 || newX >= width || newY >= height) continue
                    val newIndex = newY * width + newX
                    val pixel = pixels[newIndex]
                    val gray = Color.red(pixel)
                    val newGrayValue = (gray + error * errorDiffusionMatrix[dy][dx] / 16).coerceIn(0, 255)
                    pixels[newIndex] = Color.rgb(newGrayValue, newGrayValue, newGrayValue)
                }
            }
        }
    }

    ditheredBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return ditheredBitmap
}
