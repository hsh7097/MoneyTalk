package com.sanha.moneytalk.feature.chat.data

internal object HomeInsightNumberGuard {

    private val numberRegex = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val increaseTerms = listOf("증가", "늘", "많아")
    private val decreaseTerms = listOf("감소", "줄", "적게")

    fun matchesDeterministicClaims(insight: String, deterministicInsight: String?): Boolean {
        if (deterministicInsight == null) {
            return !numberRegex.containsMatchIn(insight)
        }

        val expectedNumbers = numberRegex.findAll(deterministicInsight)
            .map { normalize(it.value) }
            .toSet()
        val claimedNumbers = numberRegex.findAll(insight)
            .map { normalize(it.value) }
            .toSet()
        if (!expectedNumbers.containsAll(claimedNumbers)) return false

        val expectsIncrease = deterministicInsight.containsAny(increaseTerms)
        val expectsDecrease = deterministicInsight.containsAny(decreaseTerms)
        val claimsIncrease = insight.containsAny(increaseTerms)
        val claimsDecrease = insight.containsAny(decreaseTerms)

        return !(expectsIncrease && claimsDecrease) && !(expectsDecrease && claimsIncrease)
    }

    private fun normalize(value: String): String = value.replace(",", "")

    private fun String.containsAny(terms: List<String>): Boolean = terms.any(::contains)
}
