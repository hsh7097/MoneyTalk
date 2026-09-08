package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

@Composable
internal fun TransactionSameStoreRuleCard(
    uiState: TransactionEditUiState,
    hasSeenKeywordGuide: Boolean,
    onRuleKeywordChange: (String) -> Unit,
    onKeywordGuideDismiss: () -> Unit
) {
    TransactionSectionCard(
        title = stringResource(R.string.transaction_edit_same_store_rule)
    ) {
        Text(
            text = stringResource(R.string.transaction_edit_same_store_rule_desc),
            style = MaterialTheme.typography.bodySmall,
            color = TransactionEditDesignColors.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        RuleKeywordInput(
            keyword = uiState.ruleKeyword,
            onKeywordChange = onRuleKeywordChange
        )

        if (!hasSeenKeywordGuide) {
            Spacer(modifier = Modifier.height(8.dp))
            KeywordGuideCard(onDismiss = onKeywordGuideDismiss)
        }
    }
}

@Composable
private fun RuleKeywordInput(
    keyword: String,
    onKeywordChange: (String) -> Unit
) {
    val accentColor = TransactionEditDesignColors.accent
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        label = { Text(text = stringResource(R.string.transaction_edit_rule_keyword_label)) },
        placeholder = {
            Text(text = stringResource(R.string.transaction_edit_rule_keyword_placeholder))
        },
        supportingText = {
            Text(text = stringResource(R.string.transaction_edit_rule_keyword_hint))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = TransactionEditDesignColors.textPrimary
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = TransactionEditDesignColors.border,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = TransactionEditDesignColors.textSecondary,
            focusedSupportingTextColor = TransactionEditDesignColors.textSecondary,
            unfocusedSupportingTextColor = TransactionEditDesignColors.textSecondary,
            cursorColor = accentColor
        )
    )
}

@Composable
private fun KeywordGuideCard(
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.transaction_edit_keyword_guide),
                style = MaterialTheme.typography.bodySmall,
                color = TransactionEditDesignColors.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.transaction_edit_keyword_guide_confirm),
                        color = TransactionEditDesignColors.accent
                    )
                }
            }
        }
    }
}
