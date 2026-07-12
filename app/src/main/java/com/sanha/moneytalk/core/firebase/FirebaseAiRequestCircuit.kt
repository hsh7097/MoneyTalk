package com.sanha.moneytalk.core.firebase

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

internal class FirebaseAiRequestCircuit(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {
    private val blockedUntilElapsedRealtime = AtomicLong(0L)

    fun isBlocked(): Boolean = elapsedRealtime() < blockedUntilElapsedRealtime.get()

    fun blockFor(durationMs: Long) {
        val blockedUntil = elapsedRealtime() + durationMs.coerceAtLeast(0L)
        blockedUntilElapsedRealtime.updateAndGet { current -> maxOf(current, blockedUntil) }
    }
}
