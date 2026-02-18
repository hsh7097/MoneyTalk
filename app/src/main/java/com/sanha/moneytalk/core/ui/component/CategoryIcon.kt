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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.core.model.Category

/**
 * 카테고리 이모지 아이콘 컴포저블
 *
 * 원형 배경 위에 이모지를 표시합니다.
 * 카테고리별로 다른 배경색이 적용됩니다.
 *
 * @param category 표시할 카테고리
 * @param modifier 외부 Modifier
 * @param containerSize 컨테이너 크기 (기본 40dp)
 * @param fontSize 이모지 텍스트 크기 (기본 22sp)
 */
@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    fontSize: TextUnit = 22.sp
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(getCategoryBackgroundColor(category)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = category.emoji,
            fontSize = fontSize
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
        Category.FOOD, Category.DELIVERY -> Color(0xFFEF4444)  // Red 500
        Category.CAFE -> Color(0xFFF59E0B)                      // Amber 500
        Category.DRINKING -> Color(0xFFF97316)                   // Orange 500
        Category.TRANSPORT -> Color(0xFF22C55E)                  // Green 500
        Category.SHOPPING -> Color(0xFFA855F7)                   // Purple 500
        Category.SUBSCRIPTION -> Color(0xFF6366F1)               // Indigo 500
        Category.HEALTH -> Color(0xFFEC4899)                     // Pink 500
        Category.FITNESS -> Color(0xFF06B6D4)                    // Cyan 500
        Category.CULTURE -> Color(0xFF8B5CF6)                    // Violet 500
        Category.EDUCATION -> Color(0xFFEAB308)                  // Yellow 500
        Category.HOUSING -> Color(0xFFFB923C)                    // Orange 400
        Category.LIVING -> Color(0xFFF472B6)                     // Pink 400
        Category.INSURANCE -> Color(0xFF0EA5E9)                  // Sky 500
        Category.TRANSFER -> Color(0xFF3B82F6)                   // Blue 500
        Category.EVENTS -> Color(0xFFD946EF)                     // Fuchsia 500
        Category.ETC -> Color(0xFF9CA3AF)                        // Gray 400
        Category.UNCLASSIFIED -> Color(0xFF94A3B8)               // Slate 400 (분류 대기 구분)
    }
}

/**
 * 카테고리별 배경색 반환.
 * 각 카테고리의 특성에 맞는 연한 파스텔 톤을 사용한다.
 */
private fun getCategoryBackgroundColor(category: Category): Color {
    return when (category) {
        Category.FOOD, Category.DELIVERY -> Color(0xFFFEE2E2) // 연한 빨강 (식비 계열)
        Category.CAFE -> Color(0xFFFEFCE8)                    // 연한 노랑
        Category.DRINKING -> Color(0xFFFFF7ED)                 // 연한 오렌지
        Category.TRANSPORT -> Color(0xFFDCFCE7)                // 연한 초록
        Category.SHOPPING -> Color(0xFFF3E8FF)                 // 연한 보라
        Category.SUBSCRIPTION -> Color(0xFFE0E7FF)             // 연한 인디고
        Category.HEALTH -> Color(0xFFFFE4E6)                   // 연한 로즈
        Category.FITNESS -> Color(0xFFCFFAFE)                  // 연한 시안
        Category.CULTURE -> Color(0xFFEDE9FE)                  // 연한 바이올렛
        Category.EDUCATION -> Color(0xFFFEF9C3)                // 연한 옐로우
        Category.HOUSING -> Color(0xFFFFEDD5)                  // 연한 오렌지
        Category.LIVING -> Color(0xFFFCE7F3)                   // 연한 핑크
        Category.INSURANCE -> Color(0xFFE0F2FE)                // 연한 스카이
        Category.TRANSFER -> Color(0xFFDBEAFE)                 // 연한 블루
        Category.EVENTS -> Color(0xFFFCE7F3)                   // 연한 핑크
        Category.ETC -> Color(0xFFF3F4F6)                      // 연한 그레이
        Category.UNCLASSIFIED -> Color(0xFFF3F4F6)             // 연한 그레이
    }
}
