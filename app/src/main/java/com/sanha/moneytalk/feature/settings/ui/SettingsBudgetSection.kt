package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsBudgetSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
        val categoryCount = uiState.categoryBudgets.size
        val budgetText = when {
            uiState.monthlyBudget != null && categoryCount > 0 ->
                stringResource(
                    R.string.budget_subtitle_total_and_category,
                    numberFormat.format(uiState.monthlyBudget),
                    categoryCount
                )
            uiState.monthlyBudget != null ->
                stringResource(
                    R.string.settings_monthly_budget_subtitle_set,
                    numberFormat.format(uiState.monthlyBudget)
                )
            categoryCount > 0 ->
                stringResource(R.string.budget_subtitle_category_only, categoryCount)
            else ->
                stringResource(R.string.settings_monthly_budget_subtitle_empty)
        }

        SettingsSectionCompose(title = stringResource(R.string.settings_section_budget)) {
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.CalendarMonth
                    override val title = stringResource(R.string.settings_month_start_title)
                    override val subtitle = if (uiState.monthStartDay == 1) {
                        stringResource(R.string.settings_month_start_default)
                    } else {
                        stringResource(
                            R.string.settings_month_start_custom,
                            uiState.monthStartDay
                        )
                    }
                },
                onClick = { onIntent(SettingsIntent.ShowMonthStartDayDialog) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Savings
                    override val title = stringResource(R.string.settings_monthly_budget_title)
                    override val subtitle = budgetText
                },
                onClick = { onIntent(SettingsIntent.ShowBudgetBottomSheet) }
            )
        }
    } // Box (settings_period)
}
