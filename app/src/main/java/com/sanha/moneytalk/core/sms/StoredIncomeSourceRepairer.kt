package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import javax.inject.Inject

/** 재분석 시 기존 수입의 잘못된 출처만 원문으로 보정한다. */
class StoredIncomeSourceRepairer @Inject constructor(
    private val incomeRepository: IncomeRepository
) {
    suspend fun repair(range: Pair<Long, Long>): Int {
        val incomes = incomeRepository.getIncomesByDateRangeOnce(
            range.first,
            range.second
        )
        var repairedCount = 0

        for (income in incomes) {
            val originalSms = income.originalSms?.takeIf { it.isNotBlank() } ?: continue
            val parsedSource = SmsIncomeParser.extractIncomeSource(originalSms)
            val shouldRepair = when {
                parsedSource.isNotBlank() &&
                    parsedSource != income.source &&
                    SmsIncomeParser.isInvalidIncomeSource(income.source) -> true
                parsedSource.isBlank() &&
                    income.source.isNotBlank() &&
                    SmsIncomeParser.isInvalidIncomeSource(income.source) -> true
                else -> false
            }
            if (!shouldRepair) continue

            val incomeType = income.type.ifBlank { SmsIncomeParser.extractIncomeType(originalSms) }
            val description = if (parsedSource.isNotBlank()) {
                "${parsedSource}에서 $incomeType"
            } else {
                incomeType
            }
            incomeRepository.update(
                income.copy(
                    source = parsedSource,
                    description = description
                )
            )
            repairedCount++
        }

        if (repairedCount > 0) {
            MoneyTalkLogger.i("기존 수입 출처 보정 완료: ${repairedCount}건")
        }

        return repairedCount
    }

}
