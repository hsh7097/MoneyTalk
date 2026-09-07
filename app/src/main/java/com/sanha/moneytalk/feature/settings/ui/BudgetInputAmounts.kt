package com.sanha.moneytalk.feature.settings.ui

/** 현재 입력값으로 저장 금액을 계산한다. 잘못된 입력이나 범위 초과는 null로 반환한다. */
internal fun resolveCategoryBudgetAmounts(
    totalBudget: Int?,
    isPercentMode: Boolean,
    categoryAmounts: Map<String, String>,
    categoryPercents: Map<String, String>
): Map<String, Int>? {
    if (totalBudget != null && totalBudget < 0) return null
    val usePercent = isPercentMode && totalBudget != null && totalBudget > 0
    val inputs = if (usePercent) categoryPercents else categoryAmounts
    val amounts = mutableMapOf<String, Int>()
    for ((category, text) in inputs) {
        val amount = if (usePercent) {
            resolveBudgetPercentAmount(totalBudget ?: return null, text)
        } else {
            if (text.isBlank()) 0 else text.toIntOrNull()?.takeIf { it >= 0 }
        } ?: return null
        if (amount > 0) amounts[category] = amount
    }
    return amounts
}

/** 자동 변환된 100% 초과 비율도 금액이 표현 가능한 범위라면 그대로 사용한다. */
internal fun resolveBudgetPercentAmount(totalBudget: Int, percentText: String): Int? {
    if (totalBudget <= 0) return null
    val percent = if (percentText.isBlank()) 0L else percentText.toLongOrNull() ?: return null
    if (percent < 0) return null
    // 나눗셈의 원 미만 버림을 허용하되, Long 곱셈과 Int 변환 모두 넘치기 전에 검증한다.
    val maximumNumerator = Int.MAX_VALUE.toLong() * 100 + 99
    if (percent > maximumNumerator / totalBudget) return null
    return (totalBudget.toLong() * percent / 100).toInt()
}

internal fun resolveCategoryBudgetPercents(
    totalBudget: Int,
    categoryAmounts: Map<String, String>
): Map<String, String>? {
    if (totalBudget <= 0) return null
    val amounts = resolveCategoryBudgetAmounts(totalBudget, false, categoryAmounts, emptyMap())
        ?: return null
    return amounts.mapValues { (_, amount) ->
        (amount * 100L / totalBudget).toString()
    }
}
