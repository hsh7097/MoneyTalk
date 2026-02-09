package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.model.Category

private val DefaultIconColor = Color(0xFFBFC5CD)

/**
 * 카테고리 아이콘 컴포저블
 *
 * 배경 없이 벡터 아이콘만 표시합니다.
 * 컨테이너(터치 영역)와 아이콘(실제 그림) 크기를 분리하여 사용합니다.
 *
 * @param category 표시할 카테고리
 * @param modifier 외부 Modifier
 * @param containerSize 터치 영역/정렬용 컨테이너 크기 (기본 32dp)
 * @param iconSize 실제 벡터 아이콘 크기 (기본 20dp)
 * @param tint 아이콘 색상 (기본 #BFC5CD)
 */
@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    containerSize: Dp = 32.dp,
    iconSize: Dp = 20.dp,
    tint: Color = DefaultIconColor
) {
    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = category.iconRes),
            contentDescription = category.displayName,
            modifier = Modifier.size(iconSize),
            tint = tint
        )
    }
}
