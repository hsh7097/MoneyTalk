package com.sanha.moneytalk.feature.transactionedit.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import java.text.NumberFormat
import java.util.Locale

/**
 * 거래 상세/편집 공통 콘텐츠.
 *
 * 상세 화면처럼 읽히되, 거래처/금액/유형/카테고리/날짜/메모를 즉시 수정할 수 있도록
 * 시각 구조만 카드형으로 재배치한다.
 */
@Composable
internal fun TransactionEditDetailContent(
    uiState: TransactionEditUiState,
    hasSeenKeywordGuide: Boolean,
    coachMarkRegistry: CoachMarkTargetRegistry,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStoreNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryClick: () -> Unit,
    onApplyCategoryToAllChange: (Boolean) -> Unit,
    onDateTimeClick: () -> Unit,
    onMemoChange: (String) -> Unit,
    onFixedToggle: (Boolean) -> Unit,
    onStatsExcludeToggle: (Boolean) -> Unit,
    onApplyStatsExcludeToAllChange: (Boolean) -> Unit,
    onApplyFixedToAllChange: (Boolean) -> Unit,
    onRuleKeywordChange: (String) -> Unit,
    onKeywordGuideDismiss: () -> Unit
) {
    TransactionEditSystemBars()
    val hasCategorySameStoreRule = !uiState.isNew && uiState.applyCategoryToAll
    val hasAutomationSameStoreRule = !uiState.isNew &&
        (uiState.applyFixedToAll || uiState.applyStatsExcludeToAll)
    val shouldShowSameStoreRule = hasCategorySameStoreRule || hasAutomationSameStoreRule

    val onCategorySameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyCategoryToAllChange(checked)
    }
    val onFixedSameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyFixedToAllChange(checked)
    }
    val onStatsSameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyStatsExcludeToAllChange(checked)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TransactionEditDesignColors.background)
    ) {
        TransactionEditTopBar(
            isNew = uiState.isNew,
            onClose = onClose,
            onSave = onSave
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransactionHeroCard(
                uiState = uiState,
                onStoreNameChange = onStoreNameChange,
                onAmountChange = onAmountChange,
                onTypeChange = onTypeChange
            )

            TransactionBasicInfoCard(
                uiState = uiState,
                coachMarkRegistry = coachMarkRegistry,
                onCategoryClick = onCategoryClick,
                onApplyCategoryToAllChange = onCategorySameStoreChange,
                onDateTimeClick = onDateTimeClick,
                onMemoChange = onMemoChange
            )

            TransactionAutomationCard(
                uiState = uiState,
                coachMarkRegistry = coachMarkRegistry,
                onFixedToggle = onFixedToggle,
                onStatsExcludeToggle = onStatsExcludeToggle,
                onApplyFixedToAllChange = onFixedSameStoreChange,
                onApplyStatsExcludeToAllChange = onStatsSameStoreChange
            )

            AnimatedVisibility(
                visible = shouldShowSameStoreRule,
                enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 320),
                        expandFrom = Alignment.Top
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 260),
                        shrinkTowards = Alignment.Top
                    )
            ) {
                TransactionSameStoreRuleCard(
                    uiState = uiState,
                    hasSeenKeywordGuide = hasSeenKeywordGuide,
                    onRuleKeywordChange = onRuleKeywordChange,
                    onKeywordGuideDismiss = onKeywordGuideDismiss
                )
            }

            if (!uiState.isNew && uiState.originalSms.isNotBlank()) {
                TransactionOriginalSmsCard(originalSms = uiState.originalSms)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        TransactionEditBottomActions(
            isNew = uiState.isNew,
            onSave = onSave,
            onDelete = onDelete
        )
    }
}

@Composable
private fun TransactionEditTopBar(
    isNew: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = TransactionEditDesignColors.textPrimary
            )
        }
        Text(
            text = stringResource(
                if (isNew) R.string.transaction_add_title else R.string.transaction_edit_title
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TransactionEditDesignColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSave) {
            Text(
                text = stringResource(R.string.transaction_edit_save),
                color = TransactionEditDesignColors.Mint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransactionHeroCard(
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
private fun TransactionBasicInfoCard(
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
private fun TransactionAutomationCard(
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
private fun TransactionSameStoreRuleCard(
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
private fun TransactionOriginalSmsCard(originalSms: String) {
    TransactionSectionCard(
        title = stringResource(R.string.transaction_edit_original_sms)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TransactionEditDesignColors.innerCard)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                tint = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = originalSms,
                style = MaterialTheme.typography.bodySmall,
                color = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TransactionSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = TransactionEditDesignColors.card
        ),
        border = BorderStroke(1.dp, TransactionEditDesignColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TransactionEditDesignColors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (titleTrailing != null) {
                        titleTrailing()
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
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
            cursorBrush = SolidColor(TransactionEditDesignColors.Mint),
            modifier = Modifier
                .weight(1f)
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
                modifier = Modifier.size(24.dp)
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailLabel(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TransactionEditDesignColors.textPrimary,
            maxLines = 1,
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
        style = MaterialTheme.typography.bodyMedium,
        color = TransactionEditDesignColors.textSecondary,
        modifier = Modifier.width(86.dp)
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
private fun RuleKeywordInput(
    keyword: String,
    onKeywordChange: (String) -> Unit
) {
    val accentColor = TransactionEditDesignColors.Mint
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
            containerColor = TransactionEditDesignColors.Mint.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, TransactionEditDesignColors.Mint.copy(alpha = 0.2f))
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
                        color = TransactionEditDesignColors.Mint
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionEditBottomActions(
    isNew: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TransactionEditDesignColors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isNew) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TransactionEditDesignColors.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TransactionEditDesignColors.textPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TransactionEditDesignColors.Mint,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_save),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransactionDivider() {
    HorizontalDivider(color = TransactionEditDesignColors.border)
}

@Composable
private fun TransactionEditSystemBars() {
    val view = LocalView.current
    val background = TransactionEditDesignColors.background
    val isDark = FriendlyMoneyColors.isDark

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val backgroundColor = background.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.decorView.setBackgroundColor(backgroundColor)
            @Suppress("DEPRECATION")
            window.statusBarColor = backgroundColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = backgroundColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
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

@Composable
private fun TransferDirection?.toDirectionLabel(): String {
    return when (this) {
        TransferDirection.DEPOSIT -> stringResource(R.string.transfer_direction_deposit)
        TransferDirection.WITHDRAWAL, null -> stringResource(R.string.transfer_direction_withdrawal)
    }
}

private fun TransactionType.accentColor(): Color {
    return when (this) {
        TransactionType.EXPENSE -> TransactionEditDesignColors.Coral
        TransactionType.INCOME -> TransactionEditDesignColors.Mint
        TransactionType.TRANSFER -> TransactionEditDesignColors.Sky
    }
}

private object TransactionEditDesignColors {
    val Mint = Color(0xFF43B883)
    val Coral = Color(0xFFFF6B5B)
    val Honey = Color(0xFFF4B740)
    val Sky = Color(0xFF7BB8FF)

    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val card: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val innerCard: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val segmentBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant
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
