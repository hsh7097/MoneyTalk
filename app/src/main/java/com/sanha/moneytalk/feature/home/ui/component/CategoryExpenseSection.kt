package com.sanha.moneytalk.feature.home.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.feature.home.ui.model.HomeCategoryExpenseInfo
import com.sanha.moneytalk.feature.home.ui.model.HomeCategoryExpenseMapper
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.getCustomCategoryBackgroundColor
import com.sanha.moneytalk.core.ui.component.getCustomCategoryChartColor
import com.sanha.moneytalk.core.ui.component.getCategoryChartColor
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.theme.moneyTalkColors
import java.text.NumberFormat
import java.util.Locale

/** 카테고리별 지출 비율 섹션. 수평 바 차트로 각 카테고리 비율을 표시 */
@Composable
fun CategoryExpenseSection(
    categoryExpenses: List<CategorySum>,
    categoryBudgets: Map<String, Int> = emptyMap(),
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {}
) {
    var showAll by remember { mutableStateOf(false) }

    val mergedExpenses = remember(categoryExpenses, categoryBudgets) {
        HomeCategoryExpenseMapper.build(categoryExpenses, categoryBudgets)
    }

    // TOP 4 또는 전체 표시
    val displayList = remember(mergedExpenses, showAll) {
        if (showAll) mergedExpenses else mergedExpenses.take(4)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 헤더: "카테고리 TOP 4" + "전체보기/접기"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_expense_top4),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FriendlyMoneyColors.textPrimary
            )
            if (mergedExpenses.size > 4) {
                Text(
                    text = if (showAll) stringResource(R.string.home_category_view_collapse)
                    else stringResource(R.string.home_category_view_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FriendlyMoneyColors.Mint,
                    modifier = Modifier.clickable { showAll = !showAll }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (displayList.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_expense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                displayList.forEachIndexed { index, item ->
                    CategoryRankingExpenseRow(
                        item = item,
                        index = index,
                        isSelected = selectedCategory == item.category,
                        onClick = {
                            if (selectedCategory == item.category) {
                                onCategorySelected(null)
                            } else {
                                onCategorySelected(item.category)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRankingExpenseRow(
    item: HomeCategoryExpenseInfo,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val category = Category.fromDisplayName(item.category)
    val categoryEmoji = rememberCategoryEmoji(item.category)
    val isCustomCategory = category == Category.ETC && item.category != Category.ETC.displayName
    val warningColor = MaterialTheme.moneyTalkColors.calendarSunday
    val barColor = when {
        item.isOverBudget -> MaterialTheme.colorScheme.error
        item.isWarningBudget -> warningColor
        index == 0 && !item.hasBudget -> FriendlyMoneyColors.Mint
        isCustomCategory -> getCustomCategoryChartColor(item.category)
        else -> getCategoryChartColor(category)
    }
    val amountText = stringResource(R.string.common_won, numberFormat.format(item.total))
    val percentText = stringResource(R.string.home_category_share_percent, item.percentage)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) FriendlyMoneyColors.mintTint else Color.Transparent
            )
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = FriendlyMoneyColors.textSecondary,
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(4.dp))
        CategoryIcon(
            category = category,
            emojiOverride = categoryEmoji,
            backgroundColorOverride = if (isCustomCategory) {
                getCustomCategoryBackgroundColor(item.category)
            } else {
                null
            },
            containerSize = 38.dp,
            fontSize = 21.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FriendlyMoneyColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isOverBudget) {
                        MaterialTheme.colorScheme.error
                    } else {
                        FriendlyMoneyColors.textPrimary
                    },
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                item.isOverBudget -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                item.isWarningBudget -> warningColor.copy(alpha = 0.18f)
                                else -> FriendlyMoneyColors.mintTint
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isOverBudget -> MaterialTheme.colorScheme.error
                            item.isWarningBudget -> warningColor
                            else -> FriendlyMoneyColors.Mint
                        }
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(FriendlyMoneyColors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}
