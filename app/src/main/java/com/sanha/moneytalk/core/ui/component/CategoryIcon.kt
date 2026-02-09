package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.core.model.Category

/**
 * 카테고리 이모지 아이콘 컴포저블
 *
 * 배경 없이 이모지만 표시합니다.
 *
 * @param category 표시할 카테고리
 * @param modifier 외부 Modifier
 * @param containerSize 터치 영역/정렬용 컨테이너 크기 (기본 32dp)
 * @param fontSize 이모지 텍스트 크기 (기본 20sp)
 */
@Composable
fun CategoryIcon(
    category: Category,
    modifier: Modifier = Modifier,
    containerSize: Dp = 32.dp,
    fontSize: TextUnit = 20.sp
) {
    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = category.emoji,
            fontSize = fontSize
        )
    }
}
