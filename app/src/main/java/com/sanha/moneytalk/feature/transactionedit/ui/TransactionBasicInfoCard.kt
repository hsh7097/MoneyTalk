package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import java.util.Locale

@Composable
internal fun TransactionBasicInfoCard(
    uiState: TransactionEditUiState,
    coachMarkRegistry: CoachMarkTargetRegistry,
    onCategoryClick: () -> Unit,
    onApplyCategoryToAllChange: (Boolean) -> Unit,
    onDateTimeClick: () -> Unit,
    onMemoChange: (String) -> Unit
) {
    val categoryEmoji = rememberCategoryEmoji(uiState.category)
    val dateText = stringResource(
        R.string.transaction_edit_date_time_value,
        DateUtils.formatDisplayDate(uiState.dateMillis),
        String.format(Locale.KOREA, "%02d:%02d", uiState.hour, uiState.minute)
    )

    TransactionSectionCard(
        title = stringResource(R.string.transaction_edit_basic_info)
    ) {
        Column(modifier = Modifier.onboardingTarget("edit_category", coachMarkRegistry)) {
            DetailActionRow(
                label = stringResource(R.string.detail_category),
                value = "$categoryEmoji ${uiState.category}",
                onClick = onCategoryClick,
                showDivider = false
            )
            if (!uiState.isNew) {
                CompactRuleCheckbox(
                    checked = uiState.applyCategoryToAll,
                    label = stringResource(R.string.transaction_edit_apply_category_to_same_store),
                    onCheckedChange = onApplyCategoryToAllChange
                )
            }
            TransactionDivider()
        }

        if (uiState.transactionType == TransactionType.TRANSFER) {
            DetailStaticRow(
                label = stringResource(R.string.transaction_edit_transfer_direction),
                value = uiState.transferDirection.toDirectionLabel()
            )
        }

        DetailActionRow(
            label = stringResource(R.string.transaction_edit_date_time),
            value = dateText,
            onClick = onDateTimeClick
        )

        DetailEditRow(
            label = stringResource(R.string.transaction_edit_memo),
            value = uiState.memo,
            placeholder = stringResource(R.string.detail_add_memo),
            onValueChange = onMemoChange,
            singleLine = false,
            maxLines = 3,
            showDivider = false
        )
    }
}

@Composable
private fun DetailActionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    DetailRowFrame(
        label = label,
        value = value,
        modifier = Modifier.clickable(onClick = onClick),
        showDivider = showDivider,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

@Composable
private fun DetailStaticRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    DetailRowFrame(label = label, value = value, showDivider = showDivider)
}

@Composable
private fun DetailEditRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    maxLines: Int,
    showDivider: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        DetailLabel(text = label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TransactionEditDesignColors.textPrimary
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TransactionEditDesignColors.textSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (isFocused && value.isNotBlank()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_clear_input),
                    tint = TransactionEditDesignColors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
    }
    if (showDivider) {
        TransactionDivider()
    }
}

@Composable
private fun DetailRowFrame(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailLabel(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TransactionEditDesignColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        }
    }
    if (showDivider) {
        TransactionDivider()
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.toDpTextUnit),
        color = TransactionEditDesignColors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        modifier = Modifier.width(112.dp)
    )
}

@Composable
private fun CompactRuleCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TransactionEditDesignColors.textSecondary
        )
    }
}

@Composable
private fun TransferDirection?.toDirectionLabel(): String {
    return when (this) {
        TransferDirection.DEPOSIT -> stringResource(R.string.transfer_direction_deposit)
        TransferDirection.WITHDRAWAL, null -> stringResource(R.string.transfer_direction_withdrawal)
    }
}
