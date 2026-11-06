package com.shiyu.chat.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

internal data class ArtworkCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Character artwork is stored as a phone wallpaper rather than a square.
 * Existing/imported images are centre-cropped instead of stretched.
 */
internal object CharacterArtworkPolicy {
    const val OUTPUT_WIDTH = 1080
    const val OUTPUT_HEIGHT = 1920
    const val ASPECT_RATIO = OUTPUT_WIDTH.toDouble() / OUTPUT_HEIGHT

    fun sourceCrop(width: Int, height: Int): ArtworkCrop {
        require(width > 0 && height > 0) { "图片尺寸无效" }
        val sourceAspect = width.toDouble() / height
        return if (sourceAspect > ASPECT_RATIO) {
            val cropWidth = (height * ASPECT_RATIO)
                .roundToInt()
                .coerceIn(1, width)
            ArtworkCrop(
                left = (width - cropWidth) / 2,
                top = 0,
                width = cropWidth,
                height = height,
            )
        } else {
            val cropHeight = (width / ASPECT_RATIO)
                .roundToInt()
                .coerceIn(1, height)
            ArtworkCrop(
                left = 0,
                top = ((height - cropHeight) * 0.18f)
                    .roundToInt()
                    .coerceIn(0, height - cropHeight),
                width = width,
                height = cropHeight,
            )
        }
    }

    fun toPortrait(source: Bitmap): Bitmap {
        val crop = sourceCrop(source.width, source.height)
        val output = Bitmap.createBitmap(
            OUTPUT_WIDTH,
            OUTPUT_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        canvas.drawBitmap(
            source,
            Rect(
                crop.left,
                crop.top,
                crop.left + crop.width,
                crop.top + crop.height,
            ),
            RectF(
                0f,
                0f,
                OUTPUT_WIDTH.toFloat(),
                OUTPUT_HEIGHT.toFloat(),
            ),
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG or
                    Paint.DITHER_FLAG,
            ),
        )
        return output
    }
}
