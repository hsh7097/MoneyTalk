package com.sanha.moneytalk.core.model

/**
 * 거래 카테고리 (지출/수입/이체 3종 + 대/소 카테고리 계층 구조)
 *
 * [categoryType]으로 지출/수입/이체를 구분한다.
 * parentCategory가 null이면 대 카테고리, non-null이면 소 카테고리.
 */
enum class Category(
    override val emoji: String,
    override val displayName: String,
    override val categoryType: CategoryType,
    val parentCategory: Category? = null
) : CategoryInfo {
    // ===== 지출 (EXPENSE) — 22개 =====
    FOOD("\uD83C\uDF7D\uFE0F", "식비", CategoryType.EXPENSE),
    CAFE_SNACK("☕", "카페/간식", CategoryType.EXPENSE),
    DRINKING("\uD83C\uDF7A", "술/유흥", CategoryType.EXPENSE),
    LIVING("\uD83E\uDDFA", "생활", CategoryType.EXPENSE),
    ONLINE_SHOPPING("\uD83D\uDECD\uFE0F", "온라인쇼핑", CategoryType.EXPENSE),
    FASHION_SHOPPING("\uD83D\uDC5C", "패션/쇼핑", CategoryType.EXPENSE),
    BEAUTY("\uD83D\uDC84", "뷰티/미용", CategoryType.EXPENSE),
    TRANSPORT("\uD83D\uDE8C", "교통", CategoryType.EXPENSE),
    CAR("\uD83D\uDE97", "자동차", CategoryType.EXPENSE),
    HOUSING_TELECOM("\uD83C\uDFE0", "주거/통신", CategoryType.EXPENSE),
    HEALTH("\uD83C\uDFE5", "의료/건강", CategoryType.EXPENSE),
    FINANCE("\uD83D\uDCB0", "금융", CategoryType.EXPENSE),
    CULTURE("\uD83C\uDFAC", "문화/여가", CategoryType.EXPENSE),
    TRAVEL("✈\uFE0F", "여행/숙박", CategoryType.EXPENSE),
    EDUCATION_LEARNING("\uD83D\uDCDA", "교육/학습", CategoryType.EXPENSE),
    CHILDREN("\uD83D\uDC76", "자녀/육아", CategoryType.EXPENSE),
    PET("\uD83D\uDC3E", "반려동물", CategoryType.EXPENSE),
    EVENTS_GIFT("\uD83C\uDF81", "경조/선물", CategoryType.EXPENSE),
    SUBSCRIPTION("\uD83D\uDCF1", "구독", CategoryType.EXPENSE),
    FITNESS("\uD83D\uDCAA", "운동", CategoryType.EXPENSE),
    INSURANCE("\uD83D\uDCCB", "보험", CategoryType.EXPENSE),
    ETC("\uD83D\uDCE6", "기타", CategoryType.EXPENSE),

    // ===== 수입 (INCOME) — 14개 =====
    INCOME_SALARY("\uD83D\uDCB3", "급여", CategoryType.INCOME),
    INCOME_BONUS("\uD83D\uDCB0", "상여금", CategoryType.INCOME),
    INCOME_BUSINESS("\uD83D\uDCBC", "사업수입", CategoryType.INCOME),
    INCOME_PART_TIME("\uD83D\uDCC5", "아르바이트", CategoryType.INCOME),
    INCOME_ALLOWANCE("✉\uFE0F", "용돈", CategoryType.INCOME),
    INCOME_FINANCIAL("\uD83C\uDFE6", "금융수입", CategoryType.INCOME),
    INCOME_INSURANCE("\uD83D\uDEE1\uFE0F", "보험금", CategoryType.INCOME),
    INCOME_SCHOLARSHIP("\uD83C\uDF93", "장학금", CategoryType.INCOME),
    INCOME_REAL_ESTATE("\uD83C\uDFD8\uFE0F", "부동산", CategoryType.INCOME),
    INCOME_USED_TRADE("\uD83D\uDD04", "중고거래", CategoryType.INCOME),
    INCOME_SNS("\uD83D\uDCAC", "SNS", CategoryType.INCOME),
    INCOME_APP_TECH("\uD83D\uDCF1", "앱테크", CategoryType.INCOME),
    INCOME_DUTCH_PAY("➗", "더치페이", CategoryType.INCOME),
    INCOME_ETC("\uD83D\uDCB5", "기타수입", CategoryType.INCOME),

    // ===== 이체 (TRANSFER) — 8개 =====
    TRANSFER_SELF("\uD83D\uDD04", "내계좌이체", CategoryType.TRANSFER),
    TRANSFER_GENERAL("↔\uFE0F", "이체", CategoryType.TRANSFER),
    TRANSFER_CARD("\uD83D\uDCB3", "카드대금", CategoryType.TRANSFER),
    TRANSFER_SAVINGS("\uD83D\uDC37", "저축", CategoryType.TRANSFER),
    TRANSFER_CASH("\uD83D\uDCB5", "현금", CategoryType.TRANSFER),
    TRANSFER_INVESTMENT("\uD83D\uDCC8", "투자", CategoryType.TRANSFER),
    TRANSFER_LOAN("\uD83C\uDFE6", "대출", CategoryType.TRANSFER),
    TRANSFER_INSURANCE("\uD83D\uDEE1\uFE0F", "보험납입", CategoryType.TRANSFER),

    // ===== 특수 =====
    UNCLASSIFIED("⏳", "미분류", CategoryType.EXPENSE),
    INCOME_UNCLASSIFIED("⏳", "미분류", CategoryType.INCOME);

    /** 이 카테고리가 대 카테고리인지 (소 카테고리가 아닌지) */
    val isParent: Boolean get() = parentCategory == null

    /** 이 카테고리의 소 카테고리 목록 */
    val subCategories: List<Category>
        get() = entries.filter { it.parentCategory == this }

    /** 이 카테고리 + 하위 소 카테고리의 displayName 목록 (필터링용) */
    val displayNamesIncludingSub: List<String>
        get() = listOf(displayName) + subCategories.map { it.displayName }

    companion object {

        /** 레거시 displayName → 신규 Category 매핑 */
        private val legacyNameMap = mapOf(
            "카페" to CAFE_SNACK,
            "교육" to EDUCATION_LEARNING,
            "주거" to HOUSING_TELECOM,
            "경조" to EVENTS_GIFT,
            "쇼핑" to ONLINE_SHOPPING,
            "배달" to FOOD,
            "계좌이체" to TRANSFER_GENERAL,
            "AI 분류 중" to UNCLASSIFIED
        )

        /**
         * displayName으로 카테고리 조회.
         * [type]을 지정하면 해당 타입 우선 검색 (동일 displayName 충돌 해소).
         */
        fun fromDisplayName(name: String, type: CategoryType? = null): Category {
            if (name == "미분류") {
                return when (type) {
                    CategoryType.INCOME -> INCOME_UNCLASSIFIED
                    else -> UNCLASSIFIED
                }
            }
            legacyNameMap[name]?.let { return it }

            if (type != null) {
                entries.find { it.displayName == name && it.categoryType == type }
                    ?.let { return it }
            }
            return entries.find { it.displayName == name } ?: ETC
        }

        fun fromName(name: String): Category {
            return entries.find { it.name == name } ?: ETC
        }

        /** 지출 카테고리 (미분류 제외, UI 피커용) */
        val expenseEntries: List<Category>
            get() = entries.filter {
                it.categoryType == CategoryType.EXPENSE && it != UNCLASSIFIED
            }

        /** 수입 카테고리 (미분류 제외, UI 피커용) */
        val incomeEntries: List<Category>
            get() = entries.filter {
                it.categoryType == CategoryType.INCOME && it != INCOME_UNCLASSIFIED
            }

        /** 이체 카테고리 (UI 피커용) */
        val transferEntries: List<Category>
            get() = entries.filter { it.categoryType == CategoryType.TRANSFER }

        /** 지출 분류용 카테고리 목록 (미분류 제외) */
        val classifiableEntries: List<Category>
            get() = expenseEntries

        /** 지출 대 카테고리만 (UI 필터·예산 등에서 사용) */
        val parentEntries: List<Category>
            get() = expenseEntries.filter { it.isParent }
    }
}
