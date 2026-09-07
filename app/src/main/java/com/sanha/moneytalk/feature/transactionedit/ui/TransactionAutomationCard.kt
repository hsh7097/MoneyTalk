package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType

@Composable
internal fun TransactionAutomationCard(
    uiState: TransactionEditUiState,
    coachMarkRegistry: CoachMarkTargetRegistry,
    onFixedToggle: (Boolean) -> Unit,
    onStatsExcludeToggle: (Boolean) -> Unit,
    onApplyFixedToAllChange: (Boolean) -> Unit,
    onApplyStatsExcludeToAllChange: (Boolean) -> Unit
) {
    val canShowStatsExclude = uiState.transactionType != TransactionType.INCOME
    val automationSameStoreChecked = uiState.applyFixedToAll || uiState.applyStatsExcludeToAll
    val automationSameStoreVisible = !uiState.isNew &&
        (uiState.isFixed || (canShowStatsExclude && uiState.isExcludedFromStats))

    LaunchedEffect(automationSameStoreVisible, automationSameStoreChecked) {
        if (!automationSameStoreVisible && automationSameStoreChecked) {
            onApplyFixedToAllChange(false)
            onApplyStatsExcludeToAllChange(false)
        }
    }

    TransactionSectionCard(
        title = stringResource(R.string.transaction_edit_auto_organize),
        titleTrailing = if (automationSameStoreVisible) {
            {
                SameStoreHeaderCheckbox(
                    checked = automationSameStoreChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onApplyFixedToAllChange(uiState.isFixed)
                            onApplyStatsExcludeToAllChange(canShowStatsExclude && uiState.isExcludedFromStats)
                        } else {
                            onApplyFixedToAllChange(false)
                            onApplyStatsExcludeToAllChange(false)
                        }
                    }
                )
            }
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.onboardingTarget("edit_fixed", coachMarkRegistry),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AutomationOptionRow(
                title = uiState.fixedShortLabel(),
                description = uiState.fixedDescription(),
                selected = uiState.isFixed,
                onSelectedChange = { checked ->
                    onFixedToggle(checked)
                    if (automationSameStoreChecked) {
                        onApplyFixedToAllChange(checked)
                    }
                }
            )

            if (canShowStatsExclude) {
                TransactionDivider()
                AutomationOptionRow(
                    title = stringResource(R.string.transaction_edit_exclude_from_stats),
                    description = stringResource(R.string.transaction_edit_exclude_from_stats_desc),
                    selected = uiState.isExcludedFromStats,
                    onSelectedChange = { checked ->
                        onStatsExcludeToggle(checked)
                        if (automationSameStoreChecked) {
                            onApplyStatsExcludeToAllChange(checked)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AutomationOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TransactionEditDesignColors.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TransactionEditDesignColors.textSecondary
            )
        }

        Switch(
            checked = selected,
            onCheckedChange = onSelectedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TransactionEditDesignColors.Mint,
                uncheckedThumbColor = TransactionEditDesignColors.textSecondary,
                uncheckedTrackColor = TransactionEditDesignColors.innerCard,
                uncheckedBorderColor = TransactionEditDesignColors.border
            )
        )
    }
}

@Composable
private fun SameStoreHeaderCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = stringResource(R.string.transaction_edit_same_store_apply_short),
            style = MaterialTheme.typography.labelSmall,
            color = TransactionEditDesignColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransactionEditUiState.fixedShortLabel(): String {
    return when (transactionType) {
        TransactionType.INCOME -> stringResource(R.string.transaction_edit_fixed_income_short)
        TransactionType.TRANSFER -> stringResource(R.string.transaction_edit_fixed_transfer_short)
        TransactionType.EXPENSE -> stringResource(R.string.transaction_edit_fixed_expense_short)
    }
}

@Composable
private fun TransactionEditUiState.fixedDescription(): String {
    return when (transactionType) {
        TransactionType.INCOME -> stringResource(R.string.transaction_edit_fixed_income_desc)
        TransactionType.TRANSFER -> stringResource(R.string.transaction_edit_fixed_transfer_desc)
        TransactionType.EXPENSE -> stringResource(R.string.transaction_edit_fixed_expense_desc)
    }
}
