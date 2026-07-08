package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LocalChatQueryRoute(
    val type: LocalChatQueryType,
    val queries: List<DataQuery>
)

enum class LocalChatQueryType {
    TOTAL_EXPENSE,
    CATEGORY_EXPENSE,
    CATEGORY_BREAKDOWN,
    RECENT_EXPENSES,
    BUDGET_STATUS,
    UNCATEGORIZED,
    MONTHLY_TOTALS,
    DAILY_TOTALS,
    CARD_LIST,
    DUPLICATES,
    TOTAL_INCOME
}

/**
 * Gemini 호출 없이 처리해도 안전한 정형 조회만 DataQuery로 변환한다.
 * 조언/분석/수정 의도가 섞인 문장은 기존 Gemini 3-step 경로로 넘긴다.
 */
object LocalChatQueryRouter {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private const val FULL_RANGE_START_DATE = "1970-01-01"
    private const val DEFAULT_RECENT_LIMIT = 10
    private const val MAX_RECENT_LIMIT = 50

    fun tryRoute(
        message: String,
        today: LocalDate = LocalDate.now()
    ): LocalChatQueryRoute? {
        val normalized = normalize(message)
        if (normalized.isBlank()) return null
        if (hasAny(normalized, nonLookupIntentKeywords)) return null
        if (hasUnsupportedPeriod(normalized) && !isAllowedYearlyMonthlyTotals(normalized)) {
            return null
        }

        if (isBudgetStatusLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.BUDGET_STATUS,
                queries = listOf(DataQuery(type = QueryType.BUDGET_STATUS))
            )
        }

        if (isUncategorizedLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.UNCATEGORIZED,
                queries = listOf(
                    DataQuery(
                        type = QueryType.UNCATEGORIZED_LIST,
                        limit = extractLimit(normalized) ?: 20
                    )
                )
            )
        }

        if (isDuplicateLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.DUPLICATES,
                queries = listOf(DataQuery(type = QueryType.DUPLICATE_LIST))
            )
        }

        if (isCardListLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.CARD_LIST,
                queries = listOf(DataQuery(type = QueryType.CARD_LIST))
            )
        }

        if (isRecentExpenseLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.RECENT_EXPENSES,
                queries = listOf(
                    DataQuery(
                        type = QueryType.EXPENSE_LIST,
                        startDate = FULL_RANGE_START_DATE,
                        endDate = today.format(dateFormatter),
                        limit = extractLimit(normalized) ?: DEFAULT_RECENT_LIMIT
                    )
                )
            )
        }

        if (isMonthlyTotalsLookup(normalized)) {
            val yearlyRange = buildYearlyRangeIfRequested(normalized, today)
            return LocalChatQueryRoute(
                type = LocalChatQueryType.MONTHLY_TOTALS,
                queries = listOf(
                    DataQuery(
                        type = QueryType.MONTHLY_TOTALS,
                        startDate = yearlyRange?.first,
                        endDate = yearlyRange?.second
                    )
                )
            )
        }

        if (isDailyTotalsLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.DAILY_TOTALS,
                queries = listOf(DataQuery(type = QueryType.DAILY_TOTALS))
            )
        }

        if (isCategoryBreakdownLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.CATEGORY_BREAKDOWN,
                queries = listOf(DataQuery(type = QueryType.EXPENSE_BY_CATEGORY))
            )
        }

        val category = findExpenseCategory(normalized)
        if (category != null && isLookupQuestion(normalized)) {
            val queryType = if (isListLookup(normalized)) {
                QueryType.EXPENSE_LIST
            } else {
                QueryType.TOTAL_EXPENSE
            }
            return LocalChatQueryRoute(
                type = LocalChatQueryType.CATEGORY_EXPENSE,
                queries = listOf(
                    DataQuery(
                        type = queryType,
                        category = category.displayName,
                        limit = if (queryType == QueryType.EXPENSE_LIST) {
                            extractLimit(normalized) ?: DEFAULT_RECENT_LIMIT
                        } else {
                            null
                        }
                    )
                )
            )
        }

        if (isTotalIncomeLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.TOTAL_INCOME,
                queries = listOf(DataQuery(type = QueryType.TOTAL_INCOME))
            )
        }

        if (isTotalExpenseLookup(normalized)) {
            return LocalChatQueryRoute(
                type = LocalChatQueryType.TOTAL_EXPENSE,
                queries = listOf(DataQuery(type = QueryType.TOTAL_EXPENSE))
            )
        }

        return null
    }

    private fun normalize(message: String): String =
        message.trim()
            .lowercase(Locale.KOREA)
            .replace("\\s+".toRegex(), "")

    private fun isLookupQuestion(message: String): Boolean =
        hasAny(message, lookupKeywords)

    private fun isBudgetStatusLookup(message: String): Boolean =
        message.contains("예산") &&
            isLookupQuestion(message) &&
            !hasAny(message, budgetMutationKeywords)

    private fun isUncategorizedLookup(message: String): Boolean =
        message.contains("미분류") &&
            isLookupQuestion(message) &&
            !hasAny(message, uncategorizedMutationKeywords)

    private fun isDuplicateLookup(message: String): Boolean =
        message.contains("중복") &&
            hasAny(message, listOf("지출", "결제", "내역", "항목", "있어", "조회", "보여"))

    private fun isCardListLookup(message: String): Boolean =
        hasAny(message, listOf("카드목록", "카드리스트", "사용카드", "카드보여")) ||
            (message.contains("카드") && hasAny(message, listOf("목록", "리스트", "보여", "조회")))

    private fun isRecentExpenseLookup(message: String): Boolean =
        hasAny(message, listOf("최근", "마지막")) &&
            hasAny(message, listOf("지출", "결제", "소비", "내역", "항목")) &&
            isLookupQuestion(message)

    private fun isMonthlyTotalsLookup(message: String): Boolean =
        hasAny(message, listOf("월별", "월마다")) &&
            hasAny(message, expenseKeywords) &&
            isLookupQuestion(message)

    private fun isAllowedYearlyMonthlyTotals(message: String): Boolean =
        message.contains("올해") && isMonthlyTotalsLookup(message)

    private fun isDailyTotalsLookup(message: String): Boolean =
        hasAny(message, listOf("일별", "날짜별", "하루별")) &&
            hasAny(message, expenseKeywords) &&
            isLookupQuestion(message)

    private fun isCategoryBreakdownLookup(message: String): Boolean =
        hasAny(message, listOf("카테고리별", "분류별")) &&
            hasAny(message, expenseKeywords + listOf("내역", "합계")) &&
            isLookupQuestion(message)

    private fun isListLookup(message: String): Boolean =
        hasAny(message, listOf("내역", "목록", "리스트", "보여", "최근"))

    private fun isTotalIncomeLookup(message: String): Boolean =
        hasAny(message, listOf("수입", "입금")) &&
            isLookupQuestion(message) &&
            !hasAny(message, mutationKeywords)

    private fun isTotalExpenseLookup(message: String): Boolean =
        (hasAny(message, expenseKeywords) || message.contains("돈")) &&
            isLookupQuestion(message)

    private fun hasUnsupportedPeriod(message: String): Boolean =
        hasAny(
            message,
            listOf(
                "지난",
                "작년",
                "어제",
                "오늘",
                "이번주",
                "지난주",
                "전월",
                "전년",
                "개월",
                "한달",
                "월부터",
                "월까지"
            )
        )

    private fun buildYearlyRangeIfRequested(
        message: String,
        today: LocalDate
    ): Pair<String, String>? {
        if (!message.contains("올해")) return null
        return "${today.year}-01-01" to today.format(dateFormatter)
    }

    private fun findExpenseCategory(message: String): Category? =
        categoryAliases.firstOrNull { alias ->
            message.contains(alias.normalizedAlias)
        }?.category

    private fun extractLimit(message: String): Int? {
        val number = "\\d+".toRegex()
            .find(message)
            ?.value
            ?.toIntOrNull()
            ?: koreanNumberMap.entries.firstOrNull { (word, _) ->
                message.contains("${word}개") || message.contains("${word}건")
            }?.value
        return number?.coerceIn(1, MAX_RECENT_LIMIT)
    }

    private fun hasAny(message: String, keywords: List<String>): Boolean =
        keywords.any { message.contains(it) }

    private data class CategoryAlias(
        val normalizedAlias: String,
        val category: Category
    )

    private val categoryAliases: List<CategoryAlias> by lazy {
        Category.expenseEntries
            .filter { it.categoryType == CategoryType.EXPENSE }
            .flatMap { category ->
                buildCategoryAliases(category).map { alias ->
                    CategoryAlias(normalize(alias), category)
                }
            }
            .filterNot { it.normalizedAlias in ambiguousCategoryAliases }
            .distinctBy { it.normalizedAlias }
            .sortedByDescending { it.normalizedAlias.length }
    }

    private fun buildCategoryAliases(category: Category): Set<String> {
        val displayName = category.displayName
        val withoutSlash = displayName.replace("/", "")
        val aliases = mutableSetOf(displayName, withoutSlash)
        displayName.split("/").forEach { part ->
            if (part.length >= 2) {
                aliases.add(part)
                aliases.add("${part}비")
            }
        }
        if (!displayName.endsWith("비")) {
            aliases.add("${displayName}비")
            aliases.add("${withoutSlash}비")
        }
        return aliases
    }

    private val lookupKeywords = listOf(
        "얼마",
        "몇",
        "보여",
        "조회",
        "알려",
        "내역",
        "목록",
        "리스트",
        "현황",
        "합계",
        "총",
        "쓴",
        "썼",
        "사용",
        "남았",
        "남은",
        "있어"
    )

    private val expenseKeywords = listOf(
        "지출",
        "소비",
        "결제",
        "쓴",
        "썼",
        "사용"
    )

    private val nonLookupIntentKeywords = listOf(
        "분석",
        "비교",
        "추세",
        "패턴",
        "줄일",
        "줄여",
        "절약",
        "추천",
        "조언",
        "상담",
        "어때",
        "많아",
        "많은",
        "많이",
        "많은편",
        "많은지",
        "적절",
        "평가",
        "봐줘",
        "과소비",
        "낭비",
        "부담",
        "늘었",
        "늘어",
        "증가",
        "감소",
        "왜",
        "원인",
        "리포트",
        "심층"
    )

    private val mutationKeywords = listOf(
        "설정",
        "변경",
        "바꿔",
        "수정",
        "삭제",
        "추가",
        "분류"
    )

    private val budgetMutationKeywords = mutationKeywords + listOf("만원", "원으로")
    private val uncategorizedMutationKeywords = listOf(
        "변경",
        "바꿔",
        "수정",
        "삭제",
        "추가",
        "정리"
    )

    private val ambiguousCategoryAliases = setOf("쇼핑")

    private val koreanNumberMap = mapOf(
        "한" to 1,
        "두" to 2,
        "세" to 3,
        "네" to 4,
        "다섯" to 5,
        "여섯" to 6,
        "일곱" to 7,
        "여덟" to 8,
        "아홉" to 9,
        "열" to 10
    )
}
