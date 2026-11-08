package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterArtworkPolicyTest {
    @Test
    fun squareArtworkUsesCenteredPhoneWallpaperCrop() {
        assertEquals(
            ArtworkCrop(left = 112, top = 0, width = 288, height = 512),
            CharacterArtworkPolicy.sourceCrop(512, 512),
        )
    }

    @Test
    fun matchingPhoneAspectKeepsEntireImage() {
        assertEquals(
            ArtworkCrop(left = 0, top = 0, width = 1080, height = 1920),
            CharacterArtworkPolicy.sourceCrop(1080, 1920),
        )
    }

    @Test
    fun veryTallArtworkUsesCentreCropForBackground() {
        val crop = CharacterArtworkPolicy.sourceCrop(1000, 2500)

        assertEquals(1000, crop.width)
        assertEquals(1778, crop.height)
        assertEquals(361, crop.top)
    }

    @Test
    fun portraitAvatarDefaultsToUpperFaceArea() {
        val crop = CharacterArtworkPolicy.crop(
            width = 1080,
            height = 1920,
            aspectRatio = 1.0,
            selection = CharacterArtworkPolicy.DEFAULT_AVATAR,
        )

        assertEquals(0, crop.left)
        assertEquals(1080, crop.width)
        assertEquals(1080, crop.height)
        assertEquals(0, crop.top)
    }

    @Test
    fun portraitAvatarFrameMapsIntoFullWallpaperCrop() {
        val frame = CharacterArtworkPolicy.avatarFrame(
            width = 1080,
            height = 1920,
            backgroundSelection = CharacterArtworkPolicy.DEFAULT_BACKGROUND,
            avatarSelection = CharacterArtworkPolicy.DEFAULT_AVATAR,
        )

        assertEquals(0.5f, frame.centerX, 0.0001f)
        assertEquals(0.28125f, frame.centerY, 0.0001f)
        assertEquals(0.5625f, frame.diameterRatio, 0.0001f)
    }

    @Test
    fun squareSourceAvatarIsCroppedInsideSelectedBackground() {
        val crop = CharacterArtworkPolicy.avatarCropInSource(
            width = 512,
            height = 512,
            backgroundSelection = CharacterArtworkPolicy.DEFAULT_BACKGROUND,
            avatarSelection = CharacterArtworkPolicy.DEFAULT_AVATAR,
        )

        assertEquals(112, crop.left)
        assertEquals(0, crop.top)
        assertEquals(288, crop.width)
        assertEquals(288, crop.height)
    }
}
