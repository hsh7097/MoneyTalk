package com.sanha.moneytalk.receiver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Keeps one pending provider scan when a change arrives during the current scan. */
internal class ProviderChangeQueue(
    scope: CoroutineScope,
    scan: suspend () -> Unit
) {
    private val changes = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (change in changes) {
                scan()
            }
        }
    }

    fun requestScan() {
        changes.trySend(Unit)
    }
}
