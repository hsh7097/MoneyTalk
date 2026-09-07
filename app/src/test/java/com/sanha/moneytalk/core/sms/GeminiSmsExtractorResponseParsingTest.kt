package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiSmsExtractorResponseParsingTest {

    @Test
    fun `optional null metadata does not discard a valid payment`() {
        val result = requireNotNull(parseSingle(
            """{"isPayment":true,"amount":12300,"storeName":"테스트상점","cardName":null,"dateTime":null,"category":null}"""
        ))

        assertEquals(12_300, result.amount)
        assertEquals("테스트상점", result.storeName)
        assertEquals("기타", result.cardName)
        assertEquals("", result.dateTime)
        assertEquals("기타", result.category)
    }

    @Test
    fun `blank category stays unknown instead of matching the first category`() {
        assertEquals("기타", GeminiSmsExtractor.normalizeCategory("  "))
    }

    @Test
    fun `fractional overflowing or formatted amounts are not silently converted`() {
        listOf("123.45", "4294967396", "2147483648", "0", "-12300", "null", "\"12,300\"")
            .forEach { amount ->
                assertNull("invalid amount: $amount", parseSingle(
                    """{"isPayment":true,"amount":$amount,"storeName":"테스트상점"}"""
                ))
            }
    }

    @Test
    fun `an exact integral numeric string remains supported`() {
        assertEquals(12_300, parseSingle(
            """{"isPayment":true,"amount":"12300","storeName":"테스트상점"}"""
        )?.amount)
    }

    @Test
    fun `missing classification or required payment evidence is not synthesized`() {
        listOf(
            """{"amount":12300,"storeName":"테스트상점"}""",
            """{"isPayment":"true","amount":12300,"storeName":"테스트상점"}""",
            """{"isPayment":true,"storeName":"테스트상점"}""",
            """{"isPayment":true,"amount":12300,"storeName":null}""",
            """{"isPayment":true,"amount":12300,"storeName":" "}"""
        ).forEach { response -> assertNull(response, parseSingle(response)) }
    }

    @Test
    fun `minimal nonpayment remains a valid classification`() {
        assertFalse(requireNotNull(parseSingle("""{"isPayment":false}""")).isPayment)
    }

    @Test
    fun `null optional card regex does not discard required extraction patterns`() {
        val method = GeminiSmsExtractor::class.java.getDeclaredMethod(
            "parseRegexResponse", String::class.java
        ).apply { isAccessible = true }
        val result = method.invoke(
            extractor,
            """{"isPayment":true,"amountRegex":"([0-9,]+)원","storeRegex":"상점 (.+)","cardRegex":null}"""
        ) as? GeminiSmsExtractor.LlmRegexResult

        assertNotNull(result)
        assertEquals("", result?.cardRegex)
        assertEquals("([0-9,]+)원", result?.amountRegex)
    }

    @Test
    fun `batch preserves input positions and shares optional null handling`() {
        val results = requireNotNull(parseBatch(
            """[
                {"no":3,"isPayment":true,"amount":12300,"storeName":"테스트상점","cardName":null,"dateTime":null,"category":null},
                {"no":1,"isPayment":false},
                {"no":2,"isPayment":false}
            ]""",
            3
        ))

        assertEquals(3, results.size)
        assertFalse(requireNotNull(results[0]).isPayment)
        assertFalse(requireNotNull(results[1]).isPayment)
        assertEquals(12_300, results[2]?.amount)
        assertEquals("기타", results[2]?.category)
    }

    @Test
    fun `duplicate batch numbers cannot overwrite a different transaction`() {
        val results = requireNotNull(parseBatch(
            """[
                {"no":1,"isPayment":true,"amount":12300,"storeName":"테스트상점A"},
                {"no":2,"isPayment":false},
                {"no":1,"isPayment":true,"amount":45600,"storeName":"테스트상점B"},
                {"no":3,"isPayment":false},
                {"no":1,"isPayment":true,"amount":78900,"storeName":"테스트상점C"}
            ]""",
            3
        ))

        assertNull(results[0])
        assertNotNull(results[1])
        assertNotNull(results[2])
    }

    @Test
    fun `fractional or overflowing batch numbers cannot point at another SMS`() {
        val results = requireNotNull(parseBatch(
            """[
                {"no":1.5,"isPayment":true,"amount":12300,"storeName":"테스트상점A"},
                {"no":4294967297,"isPayment":true,"amount":45600,"storeName":"테스트상점B"},
                {"no":0,"isPayment":false},
                {"no":4,"isPayment":false},
                {"no":2,"isPayment":false},
                {"no":3,"isPayment":false}
            ]""",
            3
        ))

        assertNull(results[0])
        assertNotNull(results[1])
        assertNotNull(results[2])
    }

    @Test
    fun `an odd batch requires at least half of its results rounded up`() {
        assertNull(parseBatch("""[{"no":1,"isPayment":false}]""", 3))
    }

    @Test
    fun `malformed payment does not discard valid neighboring batch results`() {
        val results = requireNotNull(parseBatch(
            """[
                {"no":1,"isPayment":true,"amount":123.45,"storeName":"테스트상점"},
                {"no":2,"isPayment":false},
                {"no":3,"isPayment":false}
            ]""",
            3
        ))

        assertNull(results[0])
        assertNotNull(results[1])
        assertNotNull(results[2])
    }

    private fun parseSingle(response: String): GeminiSmsExtractor.LlmExtractionResult? {
        val method = GeminiSmsExtractor::class.java.getDeclaredMethod(
            "parseExtractionResponse", String::class.java
        ).apply { isAccessible = true }
        return method.invoke(extractor, response) as? GeminiSmsExtractor.LlmExtractionResult
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBatch(
        response: String,
        expectedSize: Int
    ): List<GeminiSmsExtractor.LlmExtractionResult?>? {
        val method = GeminiSmsExtractor::class.java.getDeclaredMethod(
            "parseBatchExtractionResponse", String::class.java, Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return method.invoke(extractor, response, expectedSize)
            as? List<GeminiSmsExtractor.LlmExtractionResult?>
    }

    // JSON 파서만 검증하므로 Android Context나 실제 AI 서비스를 초기화하지 않는다.
    private val extractor: GeminiSmsExtractor = run {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        requireNotNull(GeminiSmsExtractor::class.java.cast(
            allocateInstance.invoke(field.get(null), GeminiSmsExtractor::class.java)
        ))
    }
}
