package com.sanha.moneytalk.receiver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderBodyReaderTest {

    @Test
    fun `body completed during the last backoff is read`() = runBlocking {
        var reads = 0

        val body = ProviderBodyReader.read(longArrayOf(1, 1, 1)) {
            reads++
            if (reads == 4) "카드 승인 1,000원" else null
        }

        assertEquals("카드 승인 1,000원", body)
        assertEquals(4, reads)
    }

    @Test
    fun `ready body does not wait or read again`() = runBlocking {
        var reads = 0

        val body = ProviderBodyReader.read(longArrayOf(1, 1, 1)) {
            reads++
            "입금 2,000원"
        }

        assertEquals("입금 2,000원", body)
        assertEquals(1, reads)
    }

    @Test
    fun `missing body remains retryable after all reads`() = runBlocking {
        var reads = 0

        val body = ProviderBodyReader.read(longArrayOf(1, 1, 1)) {
            reads++
            " "
        }

        assertNull(body)
        assertEquals(4, reads)
    }
}
