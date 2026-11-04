package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayResponseTest {
    @Test
    fun oldPlainMessagesRemainUnchanged() {
        assertEquals(
            "（噗地笑出声）你可算来了。",
            naturalMessageText("（噗地笑出声）你可算来了。", null),
        )
    }

    @Test
    fun ttsDropsParentheticalStageDirections() {
        assertEquals(
            "你可算来了。坐吧。",
            speechOnlyText("（噗地笑出声）你可算来了。（拍拍椅子）坐吧。"),
        )
    }
}
