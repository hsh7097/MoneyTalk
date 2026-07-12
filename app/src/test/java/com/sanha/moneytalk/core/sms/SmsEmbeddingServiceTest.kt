package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SmsEmbeddingServiceTest {

    private val service = SmsEmbeddingService()

    @Test
    fun `embedding keeps dimension normalization and deterministic output`() {
        val first = service.createEmbedding("  STARBUCKS   Gangnam  ")
        val second = service.createEmbedding("starbucks gangnam")

        assertNotNull(first)
        assertEquals(SmsEmbeddingService.EMBEDDING_DIMENSION, first?.size)
        assertEquals(first, second)
        assertEquals(1f, norm(first.orEmpty()), 0.0001f)
    }

    @Test
    fun `whitespace differences keep the same SMS template embedding`() {
        val compact = service.createEmbedding(
            "[KB]{DATE} {TIME} {STORE} 체크카드출금 {AMOUNT} 잔액{BALANCE}"
        )
        val spaced = service.createEmbedding(
            "[KB]{DATE} {TIME} {STORE} 체크카드 출금 {AMOUNT} 잔액 {BALANCE}"
        )

        assertEquals(compact, spaced)
    }

    @Test
    fun `known merchant aliases and branches share the store embedding`() {
        val koreanBranch = service.createStoreEmbedding("스타벅스 강남점")
        val englishBranch = service.createStoreEmbedding("STARBUCKS 역삼점")

        assertNotNull(koreanBranch)
        assertEquals(koreanBranch, englishBranch)
    }

    @Test
    fun `similar SMS templates score higher than unrelated messages`() {
        val kbDebit = embedding("[KB]{DATE} {TIME} {STORE} 체크카드출금 {AMOUNT} 잔액{BALANCE}")
        val kbDebitVariant = embedding("[KB]{DATE} {TIME} {STORE} 체크카드 출금 {AMOUNT} 잔액 {BALANCE}")
        val deliveryNotice = embedding("주문하신 상품의 배송이 시작되었습니다 운송장 번호")

        assertTrue(cosine(kbDebit, kbDebitVariant) > 0.75f)
        assertTrue(cosine(kbDebit, deliveryNotice) < 0.55f)
    }

    @Test
    fun `merchant branch names score higher than unrelated merchants`() {
        val gangnam = embedding("스타벅스 강남점")
        val yeoksam = embedding("스타벅스 역삼점")
        val cityGas = embedding("서울도시가스")

        assertTrue(cosine(gangnam, yeoksam) > cosine(gangnam, cityGas))
    }

    @Test
    fun `blank input has no embedding`() {
        assertEquals(null, service.createEmbedding(" \n\t "))
    }

    private fun embedding(text: String): List<Float> {
        return requireNotNull(service.createEmbedding(text))
    }

    private fun norm(vector: List<Float>): Float {
        return sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
    }

    private fun cosine(first: List<Float>, second: List<Float>): Float {
        val dot = first.indices.sumOf { (first[it] * second[it]).toDouble() }
        return dot.toFloat()
    }
}
