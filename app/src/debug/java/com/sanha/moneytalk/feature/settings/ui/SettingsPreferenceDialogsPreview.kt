package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sanha.moneytalk.core.theme.ThemeMode

// ========== ThemeModeDialog Preview ==========

@Preview(showBackground = true, name = "테마 다이얼로그 - 시스템 선택")
@Composable
private fun ThemeModeDialogSystemPreview() {
    MaterialTheme {
        ThemeModeDialog(
            currentMode = ThemeMode.SYSTEM,
            onModeChange = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, name = "테마 다이얼로그 - 다크 선택")
@Composable
private fun ThemeModeDialogDarkPreview() {
    MaterialTheme {
        ThemeModeDialog(
            currentMode = ThemeMode.DARK,
            onModeChange = {},
            onDismiss = {}
        )
    }
}

// ========== MonthStartDayDialog Preview ==========

@Preview(showBackground = true, name = "월 시작일 - 기본값 (1일)")
@Composable
private fun MonthStartDayDialogDefaultPreview() {
    MaterialTheme {
        MonthStartDayDialog(
            initialValue = 1,
            onDismiss = {},
            onConfirm = {}
        )
    }
}

@Preview(showBackground = true, name = "월 시작일 - 커스텀 (21일)")
@Composable
private fun MonthStartDayDialogCustomPreview() {
    MaterialTheme {
        MonthStartDayDialog(
            initialValue = 21,
            onDismiss = {},
            onConfirm = {}
        )
    }
}
