package com.sanha.moneytalk.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 주어진 Dp 값을 sp로 변환하며, fontScale을 제거한 고정된 TextUnit 값을 반환합니다.
 *
 * 이 확장 프로퍼티는 `LocalDensity`를 사용하여 Dp 값을 sp로 변환하지만,
 * 글꼴 크기 조정을 나타내는 fontScale을 제거하여 고정된 크기의 TextUnit 값을 제공합니다.
 * Text Size를 Dp 기준으로 고정하고 싶을 때 사용합니다.
 *
 * @receiver 변환할 Dp 값
 * @return fontScale이 제거된 고정된 sp 값
 */
val Dp.toDpTextUnit: TextUnit
    @Composable
    get() = with(LocalDensity.current) { this@toDpTextUnit.toSp() }

/**
 * 정수형 sp 값을 fontScale이 제거된 고정된 TextUnit 값으로 변환합니다.
 *
 * 이 확장 프로퍼티는 주어진 sp 값에서 fontScale을 제거하여,
 * 고정된 TextUnit 크기를 반환합니다. 이를 통해 Text Size를
 * 특정 dp 값으로 고정하고 싶을 때 사용합니다.
 *
 * @receiver 변환할 sp 값(Int)
 * @return fontScale이 제거된 고정된 TextUnit 값
 */
val Int.toDpTextUnit: TextUnit
    @Composable
    get() = (this / LocalDensity.current.fontScale).sp
