package com.sanha.moneytalk.core.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAiRequestCircuitTest {

    @Test
    fun blockExpiresAtConfiguredElapsedTime() {
        var now = 100L
        val circuit = FirebaseAiRequestCircuit { now }

        assertFalse(circuit.isBlocked())
        circuit.blockFor(1_000L)
        assertTrue(circuit.isBlocked())

        now = 1_099L
        assertTrue(circuit.isBlocked())
        now = 1_100L
        assertFalse(circuit.isBlocked())
    }

    @Test
    fun shorterBlockDoesNotReduceExistingCooldown() {
        var now = 100L
        val circuit = FirebaseAiRequestCircuit { now }

        circuit.blockFor(1_000L)
        now = 200L
        circuit.blockFor(100L)
        now = 1_099L

        assertTrue(circuit.isBlocked())
    }
}
