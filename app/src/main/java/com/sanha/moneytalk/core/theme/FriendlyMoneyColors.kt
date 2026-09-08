package com.sanha.moneytalk.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 친근한 가계부 리디자인 전용 색상 토큰.
 *
 * 기존 Material 색상 스킴은 유지하되, 홈/가계부 핵심 카드에만 따뜻한 톤을 입힌다.
 */
object FriendlyMoneyColors {
    val Cream = Color(0xFFFFF7EA)
    val Ink = Color(0xFF20302A)
    val Mint = Color(0xFF43B883)
    val MintDeep = Color(0xFF1F7A53)
    val Coral = Color(0xFFFF6B5B)
    val Honey = Color(0xFFF4B740)
    val Sky = Color(0xFF7BB8FF)
    val Card = Color(0xFFFFFFFA)

    private val DarkCard = Color(0xFF222B26)
    private val DarkCardElevated = Color(0xFF27342D)
    private val DarkMuted = Color(0xFFAEB9B2)

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val cardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) DarkCard else Card

    val elevatedCardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) DarkCardElevated else Cream

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) MaterialTheme.colorScheme.onSurface else Ink

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) DarkMuted else Color(0xFF6B7D72)

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

    val coralTint: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) Coral.copy(alpha = 0.16f) else Coral.copy(alpha = 0.12f)
}
