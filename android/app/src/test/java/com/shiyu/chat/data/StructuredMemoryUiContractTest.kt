package com.shiyu.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StructuredMemoryUiContractTest {
    @Test
    fun memoryPageDoesNotAskUsersToApproveRoutineMemories() {
        val source = listOf(
            File("src/main/java/com/shiyu/chat/ui/ShiyuApp.kt"),
            File("app/src/main/java/com/shiyu/chat/ui/ShiyuApp.kt"),
        ).firstOrNull(File::isFile)?.readText()
            ?: error("ShiyuApp.kt not found from test working directory")
        assertFalse(source.contains("提取候选"))
        assertFalse(source.contains("确认当前事实"))
        assertFalse(source.contains("确认这是更正"))
        assertTrue(source.contains("后台自动整理"))
        assertTrue(source.contains("历史版本"))
    }
}
