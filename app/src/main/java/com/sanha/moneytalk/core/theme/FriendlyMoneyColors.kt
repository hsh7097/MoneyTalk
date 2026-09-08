package com.sanha.moneytalk.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 기존 화면의 색상 API를 공통 테마 역할에 연결한다. */
object FriendlyMoneyColors {
    val Cream = Background
    val Ink = OnSurface
    val Mint = Color(0xFF6DDBB0)
    val MintDeep = Primary
    val Coral = Error
    val Honey = Color(0xFFB47B13)
    val Sky = Color(0xFF6B93C2)
    val Card = Surface

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val cardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val elevatedCardBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val mintTint: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val mintTintContent: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimaryContainer

    val coralTint: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.errorContainer
}
