package com.sanha.moneytalk.core.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationStateTest {

    @Test
    fun tryRegisterJobAllowsOnlyOneActiveClassificationAndCleansUpOnCompletion() = runBlocking {
        val state = ClassificationState()
        val first = launch(start = CoroutineStart.LAZY) {}
        val second = launch(start = CoroutineStart.LAZY) {}

        assertTrue(state.tryRegisterJob(first))
        assertTrue(state.isRunning.value)
        assertFalse(state.tryRegisterJob(second))

        first.start()
        first.join()

        assertFalse(state.isRunning.value)
        assertTrue(state.tryRegisterJob(second))
        second.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun cancelIfRunningWaitsForCancellationCleanup() = runBlocking {
        val state = ClassificationState()
        val bodyStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            bodyStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) {
                    allowCleanupToFinish.await()
                }
            }
        }

        assertTrue(state.tryRegisterJob(job))
        job.start()
        bodyStarted.await()
        val cancellation = async { state.cancelIfRunning() }

        cleanupStarted.await()
        assertTrue(state.isRunning.value)
        assertFalse(cancellation.isCompleted)

        allowCleanupToFinish.complete(Unit)
        cancellation.await()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun lazyJobCancellationCannotLeaveRunningStateStuck() = runBlocking {
        val state = ClassificationState()
        val job = launch(start = CoroutineStart.LAZY) {}

        assertTrue(state.tryRegisterJob(job))
        job.cancelAndJoin()

        assertFalse(state.isRunning.value)
    }

    @Test
    fun replaceWithWaitsForPreviousJobBeforeRegisteringReplacement() = runBlocking {
        val state = ClassificationState()
        val bodyStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        val current = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            bodyStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) {
                    allowCleanupToFinish.await()
                }
            }
        }
        val replacement = Job()

        assertTrue(state.tryRegisterJob(current))
        current.start()
        bodyStarted.await()
        val takeover = async { state.replaceWith(replacement) }

        cleanupStarted.await()
        assertFalse(takeover.isCompleted)
        allowCleanupToFinish.complete(Unit)
        assertTrue(takeover.await())
        assertTrue(state.isRunning.value)

        replacement.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun registrationIsRejectedWhilePaused() = runBlocking {
        val state = ClassificationState()
        val initialEpoch = state.captureRegistrationEpoch()
            ?: error("initial registration epoch should be available")
        val pauseEntered = CompletableDeferred<Unit>()
        val releasePause = CompletableDeferred<Unit>()
        val pause = async {
            state.withRegistrationsPaused {
                pauseEntered.complete(Unit)
                releasePause.await()
            }
        }

        pauseEntered.await()
        assertNull(state.captureRegistrationEpoch())
        val blocked = Job()
        assertFalse(state.tryRegisterJob(blocked))
        blocked.cancelAndJoin()

        releasePause.complete(Unit)
        pause.await()
        assertFalse(state.isRegistrationEpochCurrent(initialEpoch))
        assertEquals(initialEpoch + 1L, state.captureRegistrationEpoch())

        val accepted = Job()
        assertTrue(state.tryRegisterJob(accepted))
        accepted.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun cancelledPauseWaitsForCleanupAndReopensRegistration() = runBlocking {
        val state = ClassificationState()
        val bodyStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        val blockEntered = CompletableDeferred<Unit>()
        val active = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            bodyStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) {
                    allowCleanupToFinish.await()
                }
            }
        }

        assertTrue(state.tryRegisterJob(active))
        active.start()
        bodyStarted.await()

        val pause = async {
            state.withRegistrationsPaused {
                blockEntered.complete(Unit)
            }
        }
        cleanupStarted.await()
        pause.cancel()

        val blocked = Job()
        assertFalse(state.tryRegisterJob(blocked))
        blocked.cancelAndJoin()

        allowCleanupToFinish.complete(Unit)
        pause.cancelAndJoin()
        assertFalse(blockEntered.isCompleted)

        val accepted = Job()
        assertTrue(state.tryRegisterJob(accepted))
        accepted.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun concurrentPausedSectionsAreSerialized() = runBlocking {
        val state = ClassificationState()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val first = async {
            state.withRegistrationsPaused {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            state.withRegistrationsPaused {
                secondEntered.complete(Unit)
                releaseSecond.await()
            }
        }
        assertFalse(secondEntered.isCompleted)

        releaseFirst.complete(Unit)
        first.await()
        secondEntered.await()

        val blocked = Job()
        assertFalse(state.tryRegisterJob(blocked))
        blocked.cancelAndJoin()

        releaseSecond.complete(Unit)
        second.await()

        val accepted = Job()
        assertTrue(state.tryRegisterJob(accepted))
        accepted.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }

    @Test
    fun staleEpochReplacementCannotCancelCurrentJob() = runBlocking {
        val state = ClassificationState()
        val staleEpoch = state.captureRegistrationEpoch()
            ?: error("initial registration epoch should be available")
        state.withRegistrationsPaused { }
        val currentEpoch = state.captureRegistrationEpoch()
            ?: error("current registration epoch should be available")
        val current = Job()
        val staleReplacement = Job()

        assertTrue(state.tryRegisterJob(current, expectedEpoch = currentEpoch))
        assertFalse(state.replaceWith(staleReplacement, expectedEpoch = staleEpoch))
        assertTrue(current.isActive)
        assertTrue(state.isRunning.value)

        staleReplacement.cancelAndJoin()
        current.cancelAndJoin()
        assertFalse(state.isRunning.value)
    }
}
