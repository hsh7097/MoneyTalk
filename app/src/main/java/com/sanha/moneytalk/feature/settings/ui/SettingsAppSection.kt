package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsAppSection(
    onIntent: (SettingsIntent) -> Unit,
    resetGuideModifier: Modifier = Modifier
) {
    SettingsSectionCompose(title = stringResource(R.string.settings_section_app)) {
        SettingsItemCompose(
            info = object : SettingsItemInfo {
                override val icon = Icons.Default.Info
                override val title = stringResource(R.string.settings_version_title)
                override val subtitle = BuildConfig.VERSION_NAME
            },
            onClick = { onIntent(SettingsIntent.ShowAppInfoDialog) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsItemCompose(
            info = object : SettingsItemInfo {
                override val icon = Icons.Default.Description
                override val title = stringResource(R.string.settings_privacy_title)
            },
            onClick = { onIntent(SettingsIntent.ShowPrivacyDialog) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = resetGuideModifier) {
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Refresh
                    override val title = stringResource(R.string.settings_reset_guide)
                    override val subtitle = stringResource(R.string.settings_reset_guide_subtitle)
                },
                onClick = { onIntent(SettingsIntent.ResetScreenOnboardings) }
            )
        }
    }
}
