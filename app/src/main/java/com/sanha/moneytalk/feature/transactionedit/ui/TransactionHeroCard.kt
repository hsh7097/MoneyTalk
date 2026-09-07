package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun TransactionHeroCard(
    uiState: TransactionEditUiState,
    onStoreNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit
) {
    val accentColor = uiState.transactionType.accentColor()
    val categoryEmoji = rememberCategoryEmoji(uiState.category)

    TransactionSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accentColor.copy(alpha = if (FriendlyMoneyColors.isDark) 0.2f else 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryEmoji,
                    fontSize = 26.toDpTextUnit
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EditableHeroText(
                    value = uiState.storeName,
                    placeholder = stringResource(R.string.transaction_edit_store_hint),
                    onValueChange = onStoreNameChange,
                    textColor = TransactionEditDesignColors.textPrimary,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                EditableHeroAmount(
                    amount = uiState.amount,
                    transactionType = uiState.transactionType,
                    transferDirection = uiState.transferDirection,
                    accentColor = accentColor,
                    onAmountChange = onAmountChange
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        TransactionTypeSegmentedControl(
            currentType = uiState.transactionType,
            onTypeChange = onTypeChange
        )
    }
}

@Composable
private fun EditableHeroText(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    textColor: Color,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = textColor),
            cursorBrush = SolidColor(TransactionEditDesignColors.Mint),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = TransactionEditDesignColors.textSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = TransactionEditDesignColors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun EditableHeroAmount(
    amount: String,
    transactionType: TransactionType,
    transferDirection: TransferDirection?,
    accentColor: Color,
    onAmountChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val suffix = stringResource(R.string.transaction_edit_amount_suffix)
    val prefix = when (transactionType) {
        TransactionType.INCOME -> stringResource(R.string.transaction_edit_amount_prefix_income)
        TransactionType.EXPENSE -> stringResource(R.string.transaction_edit_amount_prefix_expense)
        TransactionType.TRANSFER -> stringResource(
            if (transferDirection == TransferDirection.DEPOSIT) {
                R.string.transaction_edit_amount_prefix_income
            } else {
                R.string.transaction_edit_amount_prefix_expense
            }
        )
    }
    val amountTransformation = remember(prefix, suffix) {
        SignedAmountTransformation(prefix = prefix, suffix = suffix)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = amount,
            onValueChange = { onAmountChange(it.filter { char -> char.isDigit() }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = accentColor,
                fontWeight = FontWeight.Bold
            ),
            visualTransformation = amountTransformation,
            cursorBrush = SolidColor(accentColor),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box {
                    if (amount.isBlank()) {
                        Text(
                            text = stringResource(R.string.transaction_edit_amount_hint),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TransactionEditDesignColors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (isFocused && amount.isNotBlank()) {
            IconButton(
                onClick = { onAmountChange("") },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_clear_input),
                    tint = TransactionEditDesignColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TransactionTypeSegmentedControl(
    currentType: TransactionType,
    onTypeChange: (TransactionType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TransactionEditDesignColors.segmentBackground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TransactionType.entries.forEach { type ->
            val selected = type == currentType
            val accentColor = type.accentColor()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) accentColor else Color.Transparent)
                    .clickable { onTypeChange(type) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(type.labelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else TransactionEditDesignColors.textSecondary
                )
            }
        }
    }
}

private fun TransactionType.accentColor(): Color {
    return when (this) {
        TransactionType.EXPENSE -> TransactionEditDesignColors.Coral
        TransactionType.INCOME -> TransactionEditDesignColors.Mint
        TransactionType.TRANSFER -> TransactionEditDesignColors.Sky
    }
}

private class SignedAmountTransformation(
    private val prefix: String,
    private val suffix: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val number = original.toLongOrNull()
            ?: return TransformedText(text, OffsetMapping.Identity)
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(number)
        val output = "$prefix$formatted$suffix"

        val digitPositions = mutableListOf<Int>()
        for (i in formatted.indices) {
            if (formatted[i] != ',') {
                digitPositions.add(prefix.length + i)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset >= digitPositions.size) {
                    return prefix.length + formatted.length
                }
                return digitPositions[offset]
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= prefix.length) return 0
                var count = 0
                for (pos in digitPositions) {
                    if (pos < offset) count++ else break
                }
                return count.coerceAtMost(original.length)
            }
        }

        return TransformedText(AnnotatedString(output), offsetMapping)
    }
}
