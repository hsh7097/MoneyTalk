package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.toDpTextUnit

/**
 * 카테고리 이모지 아이콘 컴포저블
 *
 * 원형 배경 위에 이모지를 표시합니다.
 * 카테고리별로 다른 배경색이 적용됩니다.
 *
 * @param category 표시할 카테고리
 * @param modifier 외부 Modifier
 * @param emojiOverride 이모지 override (커스텀 카테고리용, null이면 category.emoji 사용)
 * @param backgroundColorOverride 배경색 override (커스텀 카테고리용, null이면 category 기반 배경색)
 * @param containerSize 컨테이너 크기 (기본 40dp)
 * @param fontSize 이모지 텍스트 크기 (기본 22dp 기준 고정)
 */
@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    emojiOverride: String? = null,
    backgroundColorOverride: Color? = null,
    containerSize: Dp = 40.dp,
    fontSize: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(backgroundColorOverride ?: getCategoryBackgroundColor(category)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emojiOverride ?: category.emoji,
            fontSize = fontSize.toDpTextUnit
        )
    }
}

/**
 * 카테고리별 차트 색상 반환.
 * 도넛 차트 arc 및 프로그레스바에 사용할 선명한(채도 높은) 색상.
 * [getCategoryBackgroundColor]와 달리 시각적 구분이 명확한 Tailwind 500 계열 색상을 사용한다.
 */
fun getCategoryChartColor(category: Category): Color {
    return when (category) {
        // 지출 (EXPENSE)
        Category.FOOD -> Color(0xFFEF4444)                       // Red 500
        Category.CAFE_SNACK -> Color(0xFFF59E0B)                 // Amber 500
        Category.DRINKING -> Color(0xFFF97316)                   // Orange 500
        Category.LIVING -> Color(0xFFF472B6)                     // Pink 400
        Category.ONLINE_SHOPPING -> Color(0xFFA855F7)            // Purple 500
        Category.FASHION_SHOPPING -> Color(0xFFC084FC)           // Purple 400
        Category.BEAUTY -> Color(0xFFF43F5E)                     // Rose 500
        Category.TRANSPORT -> Color(0xFF22C55E)                  // Green 500
        Category.CAR -> Color(0xFF10B981)                        // Emerald 500
        Category.HOUSING_TELECOM -> Color(0xFFFB923C)            // Orange 400
        Category.HEALTH -> Color(0xFFEC4899)                     // Pink 500
        Category.FINANCE -> Color(0xFF14B8A6)                    // Teal 500
        Category.CULTURE -> Color(0xFF8B5CF6)                    // Violet 500
        Category.TRAVEL -> Color(0xFF0EA5E9)                     // Sky 500
        Category.EDUCATION_LEARNING -> Color(0xFFEAB308)         // Yellow 500
        Category.CHILDREN -> Color(0xFFFB7185)                   // Rose 400
        Category.PET -> Color(0xFF34D399)                        // Emerald 400
        Category.EVENTS_GIFT -> Color(0xFFD946EF)                // Fuchsia 500
        Category.SUBSCRIPTION -> Color(0xFF6366F1)               // Indigo 500
        Category.FITNESS -> Color(0xFF06B6D4)                    // Cyan 500
        Category.INSURANCE -> Color(0xFF0284C7)                  // Sky 600
        Category.ETC -> Color(0xFF9CA3AF)                        // Gray 400
        Category.UNCLASSIFIED -> Color(0xFF94A3B8)               // Slate 400

        // 수입 (INCOME)
        Category.INCOME_SALARY -> Color(0xFF3B82F6)              // Blue 500
        Category.INCOME_BONUS -> Color(0xFF2563EB)               // Blue 600
        Category.INCOME_BUSINESS -> Color(0xFF1D4ED8)            // Blue 700
        Category.INCOME_PART_TIME -> Color(0xFF60A5FA)           // Blue 400
        Category.INCOME_ALLOWANCE -> Color(0xFF818CF8)           // Indigo 400
        Category.INCOME_FINANCIAL -> Color(0xFF059669)           // Emerald 600
        Category.INCOME_INSURANCE -> Color(0xFF0891B2)           // Cyan 600
        Category.INCOME_SCHOLARSHIP -> Color(0xFFCA8A04)         // Yellow 600
        Category.INCOME_REAL_ESTATE -> Color(0xFFEA580C)         // Orange 600
        Category.INCOME_USED_TRADE -> Color(0xFF16A34A)          // Green 600
        Category.INCOME_SNS -> Color(0xFF7C3AED)                 // Violet 600
        Category.INCOME_APP_TECH -> Color(0xFF4F46E5)            // Indigo 600
        Category.INCOME_DUTCH_PAY -> Color(0xFF0D9488)           // Teal 600
        Category.INCOME_ETC -> Color(0xFF6B7280)                 // Gray 500
        Category.INCOME_UNCLASSIFIED -> Color(0xFF94A3B8)        // Slate 400

        // 이체 (TRANSFER)
        Category.TRANSFER_SELF -> Color(0xFF3B82F6)              // Blue 500
        Category.TRANSFER_GENERAL -> Color(0xFF6366F1)           // Indigo 500
        Category.TRANSFER_CARD -> Color(0xFF8B5CF6)              // Violet 500
        Category.TRANSFER_SAVINGS -> Color(0xFF22C55E)           // Green 500
        Category.TRANSFER_CASH -> Color(0xFFF59E0B)              // Amber 500
        Category.TRANSFER_INVESTMENT -> Color(0xFFEF4444)        // Red 500
        Category.TRANSFER_LOAN -> Color(0xFFF97316)              // Orange 500
        Category.TRANSFER_INSURANCE -> Color(0xFF0EA5E9)         // Sky 500
    }
}

/**
 * 커스텀 카테고리 배경색 팔레트.
 * displayName의 해시를 기반으로 결정적(deterministic)으로 색상을 선택한다.
 * 기본 enum 배경색과 겹치지 않는 파스텔 톤 16색.
 */
private val CUSTOM_CATEGORY_PALETTE = listOf(
    Color(0xFFE8F5E9),  // Mint Green
    Color(0xFFE3F2FD),  // Light Blue
    Color(0xFFFFF3E0),  // Light Orange
    Color(0xFFF3E5F5),  // Light Purple
    Color(0xFFE0F7FA),  // Light Cyan
    Color(0xFFFBE9E7),  // Light Deep Orange
    Color(0xFFE8EAF6),  // Light Indigo
    Color(0xFFF1F8E9),  // Light Lime
    Color(0xFFFFF8E1),  // Light Amber
    Color(0xFFE1F5FE),  // Lighter Blue
    Color(0xFFFCE4EC),  // Light Pink
    Color(0xFFE0F2F1),  // Light Teal
    Color(0xFFF9FBE7),  // Light Lime Yellow
    Color(0xFFEDE7F6),  // Light Deep Purple
    Color(0xFFFFFDE7),  // Light Yellow
    Color(0xFFEFEBE9),  // Light Brown
)

/**
 * 커스텀 카테고리용 배경색 반환.
 * displayName 해시 기반으로 팔레트에서 결정적으로 선택.
 */
fun getCustomCategoryBackgroundColor(displayName: String): Color {
    val index = (displayName.hashCode() and 0x7FFFFFFF) % CUSTOM_CATEGORY_PALETTE.size
    return CUSTOM_CATEGORY_PALETTE[index]
}

/**
 * 커스텀 카테고리용 차트 색상 반환.
 * 배경색과 동일한 해시 기반이지만 채도 높은 색상.
 */
private val CUSTOM_CATEGORY_CHART_PALETTE = listOf(
    Color(0xFF4CAF50),  // Green
    Color(0xFF2196F3),  // Blue
    Color(0xFFFF9800),  // Orange
    Color(0xFF9C27B0),  // Purple
    Color(0xFF00BCD4),  // Cyan
    Color(0xFFFF5722),  // Deep Orange
    Color(0xFF3F51B5),  // Indigo
    Color(0xFF8BC34A),  // Light Green
    Color(0xFFFFC107),  // Amber
    Color(0xFF03A9F4),  // Light Blue
    Color(0xFFE91E63),  // Pink
    Color(0xFF009688),  // Teal
    Color(0xFFCDDC39),  // Lime
    Color(0xFF673AB7),  // Deep Purple
    Color(0xFFFFEB3B),  // Yellow
    Color(0xFF795548),  // Brown
)

fun getCustomCategoryChartColor(displayName: String): Color {
    val index = (displayName.hashCode() and 0x7FFFFFFF) % CUSTOM_CATEGORY_CHART_PALETTE.size
    return CUSTOM_CATEGORY_CHART_PALETTE[index]
}

/**
 * 카테고리별 배경색 반환.
 * 각 카테고리의 특성에 맞는 연한 파스텔 톤을 사용한다.
 */
private fun getCategoryBackgroundColor(category: Category): Color {
    return when (category) {
        // 지출 (EXPENSE)
        Category.FOOD -> Color(0xFFFEE2E2)                     // 연한 빨강
        Category.CAFE_SNACK -> Color(0xFFFEFCE8)               // 연한 노랑
        Category.DRINKING -> Color(0xFFFFF7ED)                  // 연한 오렌지
        Category.LIVING -> Color(0xFFFCE7F3)                    // 연한 핑크
        Category.ONLINE_SHOPPING -> Color(0xFFF3E8FF)           // 연한 보라
        Category.FASHION_SHOPPING -> Color(0xFFFAE8FF)          // 연한 퍼플
        Category.BEAUTY -> Color(0xFFFFE4E6)                    // 연한 로즈
        Category.TRANSPORT -> Color(0xFFDCFCE7)                 // 연한 초록
        Category.CAR -> Color(0xFFD1FAE5)                       // 연한 에메랄드
        Category.HOUSING_TELECOM -> Color(0xFFFFEDD5)           // 연한 오렌지
        Category.HEALTH -> Color(0xFFFFE4E6)                    // 연한 로즈
        Category.FINANCE -> Color(0xFFCCFBF1)                   // 연한 틸
        Category.CULTURE -> Color(0xFFEDE9FE)                   // 연한 바이올렛
        Category.TRAVEL -> Color(0xFFE0F2FE)                    // 연한 스카이
        Category.EDUCATION_LEARNING -> Color(0xFFFEF9C3)        // 연한 옐로우
        Category.CHILDREN -> Color(0xFFFFF1F2)                  // 연한 로즈
        Category.PET -> Color(0xFFD1FAE5)                       // 연한 에메랄드
        Category.EVENTS_GIFT -> Color(0xFFFCE7F3)               // 연한 핑크
        Category.SUBSCRIPTION -> Color(0xFFE0E7FF)              // 연한 인디고
        Category.FITNESS -> Color(0xFFCFFAFE)                   // 연한 시안
        Category.INSURANCE -> Color(0xFFE0F2FE)                 // 연한 스카이
        Category.ETC -> Color(0xFFF3F4F6)                       // 연한 그레이
        Category.UNCLASSIFIED -> Color(0xFFF3F4F6)              // 연한 그레이

        // 수입 (INCOME)
        Category.INCOME_SALARY -> Color(0xFFDBEAFE)             // 연한 블루
        Category.INCOME_BONUS -> Color(0xFFDBEAFE)              // 연한 블루
        Category.INCOME_BUSINESS -> Color(0xFFDBEAFE)           // 연한 블루
        Category.INCOME_PART_TIME -> Color(0xFFDBEAFE)          // 연한 블루
        Category.INCOME_ALLOWANCE -> Color(0xFFE0E7FF)          // 연한 인디고
        Category.INCOME_FINANCIAL -> Color(0xFFD1FAE5)          // 연한 에메랄드
        Category.INCOME_INSURANCE -> Color(0xFFCFFAFE)          // 연한 시안
        Category.INCOME_SCHOLARSHIP -> Color(0xFFFEF9C3)        // 연한 옐로우
        Category.INCOME_REAL_ESTATE -> Color(0xFFFFEDD5)        // 연한 오렌지
        Category.INCOME_USED_TRADE -> Color(0xFFDCFCE7)         // 연한 초록
        Category.INCOME_SNS -> Color(0xFFEDE9FE)                // 연한 바이올렛
        Category.INCOME_APP_TECH -> Color(0xFFE0E7FF)           // 연한 인디고
        Category.INCOME_DUTCH_PAY -> Color(0xFFCCFBF1)          // 연한 틸
        Category.INCOME_ETC -> Color(0xFFF3F4F6)                // 연한 그레이
        Category.INCOME_UNCLASSIFIED -> Color(0xFFF3F4F6)       // 연한 그레이

        // 이체 (TRANSFER)
        Category.TRANSFER_SELF -> Color(0xFFDBEAFE)             // 연한 블루
        Category.TRANSFER_GENERAL -> Color(0xFFE0E7FF)          // 연한 인디고
        Category.TRANSFER_CARD -> Color(0xFFEDE9FE)             // 연한 바이올렛
        Category.TRANSFER_SAVINGS -> Color(0xFFDCFCE7)          // 연한 초록
        Category.TRANSFER_CASH -> Color(0xFFFEFCE8)             // 연한 노랑
        Category.TRANSFER_INVESTMENT -> Color(0xFFFEE2E2)       // 연한 빨강
        Category.TRANSFER_LOAN -> Color(0xFFFFEDD5)             // 연한 오렌지
        Category.TRANSFER_INSURANCE -> Color(0xFFE0F2FE)        // 연한 스카이
    }
}
