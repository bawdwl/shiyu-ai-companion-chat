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

data class ArtworkCropSelection(
    val horizontal: Float = 0.5f,
    val vertical: Float = 0.5f,
    val zoom: Float = 1f,
) {
    fun normalized(): ArtworkCropSelection = copy(
        horizontal = horizontal.coerceIn(0f, 1f),
        vertical = vertical.coerceIn(0f, 1f),
        zoom = zoom.coerceIn(1f, 4f),
    )
}

data class AvatarFrame(
    val centerX: Float,
    val centerY: Float,
    val diameterRatio: Float,
)

internal object CharacterArtworkPolicy {
    const val OUTPUT_WIDTH = 1080
    const val OUTPUT_HEIGHT = 1920
    const val AVATAR_SIZE = 1024
    const val ASPECT_RATIO = OUTPUT_WIDTH.toDouble() / OUTPUT_HEIGHT
    val DEFAULT_BACKGROUND = ArtworkCropSelection(0.5f, 0.5f, 1f)
    val DEFAULT_AVATAR = ArtworkCropSelection(0.5f, 0.18f, 1f)

    fun crop(
        width: Int,
        height: Int,
        aspectRatio: Double,
        selection: ArtworkCropSelection = DEFAULT_BACKGROUND,
    ): ArtworkCrop {
        require(width > 0 && height > 0) { "图片尺寸无效" }
        require(aspectRatio > 0) { "裁剪比例无效" }
        val focus = selection.normalized()
        val maximumWidth = minOf(width.toDouble(), height * aspectRatio)
        val cropWidth = (maximumWidth / focus.zoom)
            .roundToInt()
            .coerceIn(1, width)
        val cropHeight = (cropWidth / aspectRatio)
            .roundToInt()
            .coerceIn(1, height)
        val centerX = (width * focus.horizontal).roundToInt()
        val centerY = (height * focus.vertical).roundToInt()
        return ArtworkCrop(
            left = (centerX - cropWidth / 2)
                .coerceIn(0, width - cropWidth),
            top = (centerY - cropHeight / 2)
                .coerceIn(0, height - cropHeight),
            width = cropWidth,
            height = cropHeight,
        )
    }

    fun sourceCrop(width: Int, height: Int): ArtworkCrop =
        crop(width, height, ASPECT_RATIO, DEFAULT_BACKGROUND)

    fun avatarFrame(
        width: Int,
        height: Int,
        backgroundSelection: ArtworkCropSelection = DEFAULT_BACKGROUND,
        avatarSelection: ArtworkCropSelection = DEFAULT_AVATAR,
    ): AvatarFrame {
        val background = crop(width, height, ASPECT_RATIO, backgroundSelection)
        val avatar = crop(
            background.width,
            background.height,
            1.0,
            avatarSelection,
        )
        return AvatarFrame(
            centerX = (
                avatar.left + avatar.width / 2f
                ) / background.width,
            centerY = (
                avatar.top + avatar.height / 2f
                ) / background.height,
            diameterRatio = avatar.height.toFloat() / background.height,
        ).let {
            it.copy(
                centerX = it.centerX.coerceIn(0f, 1f),
                centerY = it.centerY.coerceIn(0f, 1f),
                diameterRatio = it.diameterRatio.coerceIn(0.08f, 0.92f),
            )
        }
    }

    fun avatarCropInSource(
        width: Int,
        height: Int,
        backgroundSelection: ArtworkCropSelection = DEFAULT_BACKGROUND,
        avatarSelection: ArtworkCropSelection = DEFAULT_AVATAR,
    ): ArtworkCrop {
        val background = crop(width, height, ASPECT_RATIO, backgroundSelection)
        val avatar = crop(
            background.width,
            background.height,
            1.0,
            avatarSelection,
        )
        return ArtworkCrop(
            left = background.left + avatar.left,
            top = background.top + avatar.top,
            width = avatar.width,
            height = avatar.height,
        )
    }

    fun toPortrait(
        source: Bitmap,
        selection: ArtworkCropSelection = DEFAULT_BACKGROUND,
    ): Bitmap = renderCrop(
        source = source,
        outputWidth = OUTPUT_WIDTH,
        outputHeight = OUTPUT_HEIGHT,
        aspectRatio = ASPECT_RATIO,
        selection = selection,
    )

    fun toAvatar(
        source: Bitmap,
        selection: ArtworkCropSelection = DEFAULT_AVATAR,
    ): Bitmap = renderCrop(
        source = source,
        outputWidth = AVATAR_SIZE,
        outputHeight = AVATAR_SIZE,
        aspectRatio = 1.0,
        selection = selection,
    )

    private fun renderCrop(
        source: Bitmap,
        outputWidth: Int,
        outputHeight: Int,
        aspectRatio: Double,
        selection: ArtworkCropSelection,
    ): Bitmap {
        val crop = crop(source.width, source.height, aspectRatio, selection)
        val output = Bitmap.createBitmap(
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(output).drawBitmap(
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
                outputWidth.toFloat(),
                outputHeight.toFloat(),
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
