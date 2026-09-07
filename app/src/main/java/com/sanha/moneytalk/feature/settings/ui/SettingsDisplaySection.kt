package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsDisplaySection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit
) {
    val themeModeLabel = when (uiState.themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
    SettingsSectionCompose(title = stringResource(R.string.settings_section_display)) {
        SettingsItemCompose(
            info = object : SettingsItemInfo {
                override val icon = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> Icons.Default.Settings
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                }
                override val title = stringResource(R.string.settings_theme_label)
                override val subtitle = themeModeLabel
            },
            onClick = { onIntent(SettingsIntent.ShowThemeDialog) }
        )
    }
}
