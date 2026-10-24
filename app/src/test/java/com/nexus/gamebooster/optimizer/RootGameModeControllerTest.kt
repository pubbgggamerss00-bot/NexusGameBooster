package com.nexus.gamebooster.optimizer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootGameModeControllerTest {
    @Test fun invalidPackageIsRejectedBeforeRootCommand() = runBlocking {
        val result = RootGameModeController().setPerformanceMode("com.example; reboot", true)
        assertFalse(result.success)
        assertTrue(result.message.contains("valid Android package"))
    }

    @Test fun missingPackageIsRejected() = runBlocking {
        val result = RootGameModeController().setPerformanceMode("", false)
        assertFalse(result.success)
    }
}
