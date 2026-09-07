package com.sanha.moneytalk.core.sms

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmsFallbackQueueTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun input() = SmsInput("15881234_1000_hash", "카드 승인 1,000원", "15881234", 1_000)
    private fun queue(file: File) = SmsFallbackQueue(file, Gson())

    @Test
    fun `pending financial message and attempt survive process recreation`() {
        val file = File(temporaryFolder.root, "queue.json")
        val original = queue(file)
        original.enqueue(input(), original.generation(), 2_000)
        original.beginAttempt(input().id, original.generation())

        val restored = queue(file).pending().single()

        assertEquals(input(), restored.toInput())
        assertEquals(1, restored.attempts)
        assertEquals(2_000L, restored.queuedAt)
    }

    @Test
    fun `repeated receiver events do not reset finite retry count`() {
        val file = File(temporaryFolder.root, "queue.json")
        queue(file).let { it.enqueue(input(), it.generation(), 2_000) }

        repeat(SmsFallbackQueue.MAX_ATTEMPTS) { attempt ->
            val restored = queue(file)
            assertFalse(restored.enqueue(input(), restored.generation(), 3_000))
            assertEquals(attempt + 1, restored.beginAttempt(input().id, restored.generation())?.attempts)
        }

        val exhausted = queue(file)
        assertTrue(exhausted.pending().isEmpty())
        assertNull(exhausted.beginAttempt(input().id, exhausted.generation()))
        assertFalse(exhausted.enqueue(input(), exhausted.generation(), 4_000))
        assertFalse(file.readText().contains(input().body))
        assertFalse(file.readText().contains(input().address))
    }

    @Test
    fun `last attempt retains its input only in memory until it succeeds`() {
        val file = File(temporaryFolder.root, "queue.json")
        val pending = queue(file)
        pending.enqueue(input(), pending.generation(), 2_000)
        repeat(SmsFallbackQueue.MAX_ATTEMPTS - 1) {
            pending.beginAttempt(input().id, pending.generation())
        }

        val finalAttempt = pending.beginAttempt(input().id, pending.generation())
        assertEquals(input(), finalAttempt?.toInput())
        pending.complete(input().id, pending.generation())

        assertFalse(file.readText().contains(input().id))
    }

    @Test
    fun `data deletion invalidates in-flight enqueue and completion tokens`() {
        val file = File(temporaryFolder.root, "queue.json")
        val pending = queue(file)
        val oldGeneration = pending.generation()
        pending.enqueue(input(), oldGeneration, 2_000)
        pending.clear()

        val restored = queue(file)
        assertTrue(restored.pending().isEmpty())
        assertFalse(restored.enqueue(input(), oldGeneration, 3_000))
        assertTrue(restored.enqueue(input(), restored.generation(), 4_000))
        restored.complete(input().id, oldGeneration)
        assertEquals(1, restored.pending().size)
    }

    @Test
    fun `success removes payload from durable queue`() {
        val file = File(temporaryFolder.root, "queue.json")
        val pending = queue(file)
        pending.enqueue(input(), pending.generation(), 2_000)

        pending.complete(input().id, pending.generation())

        assertTrue(queue(file).pending().isEmpty())
        assertFalse(file.readText().contains(input().body))
    }

    @Test
    fun `interrupted replacement keeps last committed queue`() {
        val file = File(temporaryFolder.root, "queue.json")
        val pending = queue(file)
        pending.enqueue(input(), pending.generation(), 2_000)
        File(file.path + ".tmp").writeText("incomplete replacement")

        assertEquals(input(), queue(file).pending().single().toInput())
    }
}
