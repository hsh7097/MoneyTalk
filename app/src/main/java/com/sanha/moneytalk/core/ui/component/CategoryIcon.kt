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
