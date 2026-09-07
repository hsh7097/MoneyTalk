package com.sanha.moneytalk.receiver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderChangeQueueTest {

    @Test
    fun `message arriving during a scan is picked up by one follow-up scan`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val scanStarted = CompletableDeferred<Unit>()
        val finishFirstScan = CompletableDeferred<Unit>()
        val rescanFinished = CompletableDeferred<Unit>()
        val providerMessages = mutableListOf("first")
        val processedMessages = mutableSetOf<String>()
        var scanCount = 0
        try {
            val queue = ProviderChangeQueue(scope) {
                scanCount++
                val snapshot = providerMessages.toList()
                if (scanCount == 1) {
                    scanStarted.complete(Unit)
                    finishFirstScan.await()
                }
                processedMessages.addAll(snapshot)
                if (scanCount == 2) rescanFinished.complete(Unit)
            }

            queue.requestScan()
            withTimeout(1_000) { scanStarted.await() }
            providerMessages.add("second")
            repeat(20) { queue.requestScan() }
            finishFirstScan.complete(Unit)
            withTimeout(1_000) { rescanFinished.await() }
            yield()

            assertEquals(setOf("first", "second"), processedMessages)
            assertEquals(2, scanCount)
        } finally {
            scope.cancel()
        }
    }
}
