package com.sanha.moneytalk.feature.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 기존 홈 표면·장식 색상과 그라데이션 위에서 읽을 수 있는 금액 의미색. */
internal object HomeColors {
    val Mint = Color(0xFF43B883)
    val MintDeep = Color(0xFF1F7A53)
    val Coral = Color(0xFFFF6B5B)
    val Honey = Color(0xFFF4B740)
    // 고정된 밝은 그라데이션 위의 금액도 같은 빨강/파랑 의미를 유지한다.
    val IncomeOnGradient = Color(0xFF5C0820)
    val ExpenseOnGradient = Color(0xFF031E4A)

    private val Ink = Color(0xFF20302A)

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val elevatedCardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Color(0xFF27342D) else Color(0xFFFFF7EA)

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) MaterialTheme.colorScheme.onSurface else Ink

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Color(0xFFAEB9B2) else Color(0xFF6B7D72)

    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Mint.copy(alpha = 0.22f) else Ink.copy(alpha = 0.08f)

    val mintTint: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Mint.copy(alpha = 0.16f) else Mint.copy(alpha = 0.12f)

    val mintTintContent: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Color(0xFFEAF7EE) else MintDeep
}
