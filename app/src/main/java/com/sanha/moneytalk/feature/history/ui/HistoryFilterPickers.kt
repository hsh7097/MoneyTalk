package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.model.CategoryInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardFilterListBottomSheet(
    cardNames: List<String>,
    selectedCardNames: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactSheetHeight = screenHeight * 0.68f
    val expandedSheetHeight = screenHeight - 72.dp
    var isExpanded by remember { mutableStateOf(false) }
    val sheetHeight = if (isExpanded) expandedSheetHeight else compactSheetHeight
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectionSummary = if (selectedCardNames.isEmpty()) {
        stringResource(R.string.history_filter_all_cards)
    } else {
        stringResource(R.string.history_filter_selected_count, selectedCardNames.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_filter_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FriendlyMoneyColors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isExpanded = !isExpanded }) {
                            Text(
                                text = stringResource(
                                    if (isExpanded) {
                                        R.string.history_filter_collapse_sheet
                                    } else {
                                        R.string.history_filter_expand_sheet
                                    }
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = FriendlyMoneyColors.Mint
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = FriendlyMoneyColors.textSecondary
                            )
                        }
                    }
                }
                Text(
                    text = selectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = FriendlyMoneyColors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 0.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        CategoryFilterListRow(
                            emoji = null,
                            label = stringResource(R.string.history_filter_all_cards),
                            checked = selectedCardNames.isEmpty(),
                            onCheckedChange = { onSelectionChanged(emptySet()) }
                        )
                    }

                    items(cardNames, key = { it }) { cardName ->
                        val isChecked = selectedCardNames.contains(cardName)
                        CategoryFilterListRow(
                            emoji = null,
                            label = cardName,
                            checked = isChecked,
                            onCheckedChange = {
                                val next = if (isChecked) {
                                    selectedCardNames - cardName
                                } else {
                                    selectedCardNames + cardName
                                }
                                onSelectionChanged(next)
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryFilterListBottomSheet(
    sheetType: CategorySheetType,
    categories: List<CategoryInfo>,
    isTypeChecked: Boolean,
    selectedCategories: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactSheetHeight = screenHeight * 0.68f
    val expandedSheetHeight = screenHeight - 72.dp
    var isExpanded by remember { mutableStateOf(false) }
    val sheetHeight = if (isExpanded) expandedSheetHeight else compactSheetHeight
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectionSummary = if (selectedCategories.isEmpty()) {
        stringResource(R.string.history_filter_all_categories)
    } else {
        stringResource(R.string.history_filter_selected_count, selectedCategories.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(sheetType.titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FriendlyMoneyColors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isExpanded = !isExpanded }) {
                            Text(
                                text = stringResource(
                                    if (isExpanded) {
                                        R.string.history_filter_collapse_sheet
                                    } else {
                                        R.string.history_filter_expand_sheet
                                    }
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = FriendlyMoneyColors.Mint
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = FriendlyMoneyColors.textSecondary
                            )
                        }
                    }
                }
                Text(
                    text = selectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = FriendlyMoneyColors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 0.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        CategoryFilterListRow(
                            emoji = null,
                            label = stringResource(R.string.history_filter_all_categories),
                            checked = isTypeChecked && selectedCategories.isEmpty(),
                            onCheckedChange = { onSelectionChanged(emptySet()) }
                        )
                    }

                    items(categories, key = { it.displayName }) { category ->
                        val isChecked = selectedCategories.contains(category.displayName)
                        CategoryFilterListRow(
                            emoji = category.emoji,
                            label = category.displayName,
                            checked = isChecked,
                            onCheckedChange = {
                                val next = if (isChecked) {
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterListRow(
    emoji: String?,
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .clickable { onCheckedChange() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
        if (emoji != null) {
            Text(text = emoji, modifier = Modifier.padding(end = 6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
