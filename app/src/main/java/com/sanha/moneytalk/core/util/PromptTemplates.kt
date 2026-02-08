package com.sanha.moneytalk.core.util

object PromptTemplates {

    // 문자 분석용 프롬프트
    fun analyzeSms(smsText: String, referenceText: String = "") = """
        다음 카드 결제 문자를 분석해서 JSON으로 반환해줘.

        문자: $smsText

        반환 형식:
        {
            "amount": 금액(숫자만),
            "storeName": "가게명",
            "category": "카테고리",
            "dateTime": "YYYY-MM-DD HH:mm",
            "cardName": "카드사"
        }

        카테고리는 다음 중 하나만 선택해:
        - 식비 (음식점, 마트 식품)
        - 배달 (배달의민족, 요기요, 쿠팡이츠)
        - 카페 (커피숍, 디저트)
        - 술/유흥 (술집, 바, 호프, 노래방)
        - 교통 (택시, 주유, 대중교통)
        - 쇼핑 (의류, 잡화, 온라인쇼핑)
        - 구독 (넷플릭스, 유튜브, 앱결제)
        - 의료/건강 (병원, 약국)
        - 운동 (헬스장, 피트니스)
        - 문화/여가 (영화, 게임, 여행)
        - 교육 (학원, 책, 강의)
        - 주거 (월세, 관리비)
        - 생활 (공과금, 통신비, 생필품)
        - 보험 (보험료 납부)
        - 계좌이체 (명시적 계좌이체만, 체크카드출금은 일반 결제)
        - 경조 (축의금, 조의금)
        - 기타 (위에 해당 안되는 경우)
        $referenceText
        JSON만 반환하고 다른 텍스트는 포함하지 마.
    """.trimIndent()

    // 재무 상담용 프롬프트
    fun financialAdvice(
        monthlyIncome: Int,
        totalExpense: Int,
        categoryBreakdown: String,
        recentExpenses: String,
        userQuestion: String,
        periodContext: String = "이번 달"
    ) = """
        너는 친근한 개인 재무 상담사 '머니톡'이야.

        [조회 기간: $periodContext]

        [사용자 재무 현황]
        - 월 수입: ${formatCurrency(monthlyIncome)}
        - $periodContext 지출: ${formatCurrency(totalExpense)}
        ${if (periodContext.contains("월") && !periodContext.contains("~")) "- 남은 예산: ${formatCurrency(monthlyIncome - totalExpense)}" else ""}

        [카테고리별 지출]
        $categoryBreakdown

        [$periodContext 지출 내역]
        $recentExpenses

        [사용자 질문]
        $userQuestion

        답변 규칙:
        1. 친근하게 반말로 답변해줘
        2. 구체적인 숫자와 함께 실용적인 조언을 해줘
        3. 이모지를 적절히 사용해줘
        4. 답변은 간결하게, 핵심만 전달해줘
        5. 긍정적이고 응원하는 톤을 유지해줘
        6. 질문에서 요청한 기간($periodContext)에 맞게 답변해줘
    """.trimIndent()

    // 소비 패턴 분석 프롬프트
    fun analyzePattern(
        weeklyExpenses: String,
        categoryTrend: String
    ) = """
        다음 소비 데이터를 분석해서 인사이트를 제공해줘.

        [주간 지출 추이]
        $weeklyExpenses

        [카테고리별 추이]
        $categoryTrend

        다음 형식으로 답변해줘:
        1. 주요 소비 패턴 (1-2줄)
        2. 특이사항 (평소와 다른 점)
        3. 절약 팁 (구체적인 금액 포함)

        친근하게 반말로, 이모지 포함해서 답변해줘.
    """.trimIndent()

    private fun formatCurrency(amount: Int): String {
        return String.format("%,d원", amount)
    }
}
