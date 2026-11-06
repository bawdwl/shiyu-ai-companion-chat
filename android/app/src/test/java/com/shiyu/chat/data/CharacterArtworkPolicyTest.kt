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
    fun veryTallArtworkKeepsUpperCentreSubject() {
        val crop = CharacterArtworkPolicy.sourceCrop(1000, 2500)

        assertEquals(1000, crop.width)
        assertEquals(1778, crop.height)
        assertEquals(130, crop.top)
    }
}
