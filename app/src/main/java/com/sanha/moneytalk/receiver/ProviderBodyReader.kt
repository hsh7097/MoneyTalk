package com.sanha.moneytalk.receiver

import kotlinx.coroutines.delay

/** Reads once immediately and once after every provider-write backoff. */
internal object ProviderBodyReader {
    suspend fun read(
        retryDelays: LongArray,
        readBody: () -> String?
    ): String? {
        readBody()?.takeIf { it.isNotBlank() }?.let { return it }
        for (delayMs in retryDelays) {
            delay(delayMs)
            readBody()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
