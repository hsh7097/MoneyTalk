package com.sanha.moneytalk.core.ui

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

/**
 * App-wide snackbar event bus.
 *
 * Why: Avoid storing one-off messages in per-screen UI state.
 * When a screen leaves composition while a snackbar is being shown,
 * the coroutine is cancelled and the message may reappear on re-entry.
 *
 * Using a SharedFlow makes the message an event (not state),
 * and displaying it from the app root makes it not screen-dependent (toast-like).
 */
@Singleton
class AppSnackbarBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppSnackbarEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    fun show(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        _events.tryEmit(
            AppSnackbarEvent(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration
            )
        )
    }
}

