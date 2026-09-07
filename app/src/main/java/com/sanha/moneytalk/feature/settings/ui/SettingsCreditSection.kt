package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsCreditSection(balance: Int, onOpenCredit: () -> Unit) {
    SettingsSectionCompose(title = stringResource(R.string.settings_section_ai)) {
        SettingsItemCompose(
            info = object : SettingsItemInfo {
                override val icon = Icons.Default.AccountBalanceWallet
                override val title = stringResource(R.string.settings_ai_credit_title)
                override val subtitle = stringResource(
                    R.string.settings_ai_credit_subtitle,
                    balance
                )
            },
            onClick = {
                onOpenCredit()
            }
        )
    }
}
