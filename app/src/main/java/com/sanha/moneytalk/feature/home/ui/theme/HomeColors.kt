package com.sanha.moneytalk.feature.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 9b4b4bf에서 사용한 홈 표면과 장식 색상. */
internal object HomeColors {
    val Mint = Color(0xFF43B883)
    val MintDeep = Color(0xFF1F7A53)
    val Coral = Color(0xFFFF6B5B)
    val Honey = Color(0xFFF4B740)

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
