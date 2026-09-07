package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.util.toDpTextUnit

private fun filterTypeIcon(type: FilterTransactionType) = when (type) {
    FilterTransactionType.ALL -> Icons.Default.RadioButtonChecked
    FilterTransactionType.EXPENSE -> Icons.Default.ArrowDownward
    FilterTransactionType.INCOME -> Icons.Default.ArrowUpward
    FilterTransactionType.TRANSFER -> Icons.Default.SwapHoriz
}

private fun filterTypeAccentColor(type: FilterTransactionType): Color = when (type) {
    FilterTransactionType.ALL -> FriendlyMoneyColors.Honey
    FilterTransactionType.EXPENSE -> FriendlyMoneyColors.Coral
    FilterTransactionType.INCOME -> FriendlyMoneyColors.Mint
    FilterTransactionType.TRANSFER -> FriendlyMoneyColors.Sky
}

@Composable
internal fun FilterTransactionTypeSelector(
    showExpenses: Boolean,
    showIncomes: Boolean,
    showTransfers: Boolean,
    onTypeClick: (FilterTransactionType) -> Unit
) {
    val allSelected = showExpenses && showIncomes && showTransfers

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterTransactionType.entries.forEach { type ->
            val selected = when (type) {
                FilterTransactionType.ALL -> allSelected
                FilterTransactionType.EXPENSE -> !allSelected && showExpenses
                FilterTransactionType.INCOME -> !allSelected && showIncomes
                FilterTransactionType.TRANSFER -> !allSelected && showTransfers
            }

            FilterTypeTile(
                type = type,
                label = stringResource(type.labelResId),
                selected = selected,
                onClick = { onTypeClick(type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun FilterGuideCard(
    modifier: Modifier = Modifier
) {
    val guideShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(guideShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.24f else 0.16f),
                        FriendlyMoneyColors.Sky.copy(alpha = if (FriendlyMoneyColors.isDark) 0.12f else 0.08f)
                    )
                )
            )
            .border(1.dp, FriendlyMoneyColors.Mint.copy(alpha = 0.22f), guideShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FriendlyMoneyColors.Mint.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history_filter_guide_icon),
                fontSize = 18.toDpTextUnit
            )
        }
        Text(
            text = stringResource(R.string.history_filter_guide_text),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.toDpTextUnit,
                lineHeight = 18.toDpTextUnit
            ),
            fontWeight = FontWeight.SemiBold,
            color = FriendlyMoneyColors.textPrimary
        )
    }
}

@Composable
private fun FilterTypeTile(
    type: FilterTransactionType,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val accentColor = filterTypeAccentColor(type)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.82f else 0.20f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderColor = if (selected) {
        FriendlyMoneyColors.Mint
    } else {
        FriendlyMoneyColors.border
    }
    val iconColor = if (selected) {
        when (type) {
            FilterTransactionType.ALL -> FriendlyMoneyColors.Ink
            FilterTransactionType.EXPENSE -> FriendlyMoneyColors.Coral
            FilterTransactionType.INCOME -> FriendlyMoneyColors.MintDeep
            FilterTransactionType.TRANSFER -> FriendlyMoneyColors.Ink
        }
    } else {
        FriendlyMoneyColors.textSecondary
    }
    val textColor = if (selected) {
        if (FriendlyMoneyColors.isDark) FriendlyMoneyColors.Ink else FriendlyMoneyColors.textPrimary
    } else {
        FriendlyMoneyColors.textSecondary
    }

    Column(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = filterTypeIcon(type),
            contentDescription = null,
            tint = if (selected) iconColor else accentColor.copy(alpha = 0.58f),
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun FilterNoticeCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.16f else 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = FriendlyMoneyColors.Mint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = FriendlyMoneyColors.Mint
        )
    }
}

@Composable
internal fun FilterOptionPillRow(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterOptionPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onOptionSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.78f else 0.18f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected && FriendlyMoneyColors.isDark) FriendlyMoneyColors.Ink else {
        if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.textSecondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.border
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterCategoryChipGroup(
    sheetType: CategorySheetType,
    categories: List<CategoryInfo>,
    selectedCategories: Set<String>,
    allText: String,
    onSelectionChanged: (Set<String>) -> Unit,
    onMoreClick: () -> Unit
) {
    val previewCategories = (categories.filter { selectedCategories.contains(it.displayName) } +
            categories.filterNot { selectedCategories.contains(it.displayName) })
        .distinctBy { it.displayName }
        .take(6)

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(sheetType.titleResId),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.toDpTextUnit),
                fontWeight = FontWeight.SemiBold,
                color = FriendlyMoneyColors.textPrimary
            )
            Text(
                text = stringResource(R.string.history_filter_more_categories),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = FriendlyMoneyColors.Mint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMoreClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChoiceChip(
                label = allText,
                selected = selectedCategories.isEmpty(),
                onClick = { onSelectionChanged(emptySet()) }
            )
            previewCategories.forEach { category ->
                CategoryChoiceChip(
                    label = category.displayName,
                    emoji = category.emoji,
                    selected = selectedCategories.contains(category.displayName),
                    onClick = {
                        val next = if (selectedCategories.contains(category.displayName)) {
                            selectedCategories - category.displayName
                        } else {
                            selectedCategories + category.displayName
                        }
                        onSelectionChanged(next)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    emoji: String? = null
) {
    val shape = RoundedCornerShape(11.dp)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderColor = if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.border
    val textColor = if (selected) {
        FriendlyMoneyColors.Mint
    } else {
        FriendlyMoneyColors.textSecondary
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    fontSize = 15.toDpTextUnit
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = FriendlyMoneyColors.Mint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
internal fun FilterCategorySummaryRow(
    label: String,
    summary: String,
    onCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onCategoryClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = FriendlyMoneyColors.textPrimary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = FriendlyMoneyColors.textPrimary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FriendlyMoneyColors.textSecondary
            )
        }
    }
}
