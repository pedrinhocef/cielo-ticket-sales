package com.pedrosoares.cielosales.core.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object TicketQrCodeGenerator {
    private const val DEFAULT_QR_CODE_SIZE_PX = 512

    fun generate(
        content: String,
        size: Int = DEFAULT_QR_CODE_SIZE_PX,
        onFailure: (Exception) -> Unit = {}
    ): Bitmap? = try {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(bitMatrix.width * bitMatrix.height)
        for (y in 0 until bitMatrix.height) {
            val offset = y * bitMatrix.width
            for (x in 0 until bitMatrix.width) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        createBitmap(bitMatrix.width, bitMatrix.height).apply {
            setPixels(pixels, 0, bitMatrix.width, 0, 0, bitMatrix.width, bitMatrix.height)
        }
    } catch (exception: Exception) {
        onFailure(exception)
        null
    }
}
