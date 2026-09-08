package com.sanha.moneytalk.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.home.briefing.SpendingBriefingCard
import com.sanha.moneytalk.feature.home.recurring.RecurringExpenseForecastCard
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.cta.FullSyncCtaSection
import com.sanha.moneytalk.core.ui.component.cta.ImportDataCtaSection
import com.sanha.moneytalk.feature.home.ui.component.SpendingTrendSection
import com.sanha.moneytalk.feature.home.ui.model.HomeSpendingTrendInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import com.sanha.moneytalk.feature.home.ui.component.MonthlyOverviewSection
import com.sanha.moneytalk.feature.home.ui.component.CategoryExpenseSection

/**
 * 홈 HorizontalPager의 각 페이지 콘텐츠.
 * pageCache에서 가져온 HomePageData를 기반으로 월간 현황, 카테고리, 오늘 거래 등을 렌더링.
 */
@Composable
fun HomePageContent(
    pageData: HomePageData,
    year: Int,
    month: Int,
    monthStartDay: Int,
    isMonthSynced: Boolean,
    isPartiallyCovered: Boolean,
    hasSmsPermission: Boolean,
    selectedCategory: String?,
    isSyncing: Boolean,
    isAdEnabled: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncrementalSync: () -> Unit,
    onFullSync: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onExpenseSelected: (ExpenseEntity) -> Unit,
    onIncomeSelected: (IncomeEntity) -> Unit,
    coachMarkRegistry: CoachMarkTargetRegistry? = null,
    isCurrentPage: Boolean = false,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onTransactionLongClick: ((TransactionTarget) -> Unit)? = null,
    onForecastTransactionClick: (Long) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    // 오늘 지출 + 수입을 시간순 통합 (pageData 기준)
    val todayTransactions = remember(
        pageData.todayExpenses,
        pageData.todayIncomes
    ) {
        val items = mutableListOf<TodayItem>()
        pageData.todayExpenses.forEach { items.add(TodayItem.Expense(it)) }
        pageData.todayIncomes.forEach { items.add(TodayItem.Income(it)) }
        items.sortedByDescending { item ->
            when (item) {
                is TodayItem.Expense -> item.expense.dateTime
                is TodayItem.Income -> item.income.dateTime
            }
        }
    }
    val currentMonthSyncLabel = stringResource(R.string.home_current_month_sync_label)
    val syncMonthLabelFormat = stringResource(R.string.home_sync_month_label_format)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CTA 표시 조건 계산
            val (effYearCta, effMonthCta) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
            val isCurrentMonth = year == effYearCta && month == effMonthCta
            val hasNoData = !pageData.isLoading &&
                    pageData.monthlyExpense == 0 && pageData.monthlyIncome == 0

            // 데이터 가져오기 CTA:
            // 현재월 + 권한 없음 또는 아직 해당 월 전체 확인 전의 빈 화면
            val showImportCta = isCurrentMonth &&
                    (!hasSmsPermission || (hasNoData && !isMonthSynced))

            // 과거 월 데이터 가져오기 CTA (광고 시청 → 월별 동기화)
            val ctaMonthLabel = if (isCurrentMonth) {
                currentMonthSyncLabel
            } else {
                String.format(syncMonthLabelFormat, month)
            }
            val showPastMonthSyncCta = !showImportCta &&
                    !isCurrentMonth &&
                    !isMonthSynced

            // ━━━ BLOCK 1: CTA — 필요할 때 홈 최상단에 노출 ━━━
            if (showImportCta) {
                item {
                    val targetModifier = if (isCurrentPage && coachMarkRegistry != null) {
                        Modifier.onboardingTarget("home_sync_cta", coachMarkRegistry)
                    } else Modifier
                    Box(modifier = targetModifier) {
                        ImportDataCtaSection(
                            onImportData = onIncrementalSync,
                            isSyncing = isSyncing
                        )
                    }
                }
            }

            if (showPastMonthSyncCta) {
                item {
                    FullSyncCtaSection(
                        onRequestFullSync = onFullSync,
                        monthLabel = ctaMonthLabel,
                        isPartial = isPartiallyCovered,
                        isSyncing = isSyncing,
                        isAdEnabled = isAdEnabled
                    )
                }
            }

            // ━━━ BLOCK 2: Hero Summary ━━━
            item {
                val targetModifier = if (isCurrentPage && coachMarkRegistry != null) {
                    Modifier.onboardingTarget("home_overview", coachMarkRegistry)
                } else Modifier
                Box(modifier = targetModifier) {
                    MonthlyOverviewSection(
                        year = year,
                        month = month,
                        monthStartDay = monthStartDay,
                        periodLabel = pageData.periodLabel,
                        income = pageData.monthlyIncome,
                        expense = pageData.monthlyExpense,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth
                    )
                }
            }

            pageData.spendingBriefing?.let { briefing ->
                item {
                    SpendingBriefingCard(
                        briefing = briefing,
                        isPartialCoverage = isPartiallyCovered || !isMonthSynced,
                        hasSmsPermission = hasSmsPermission,
                        onCategoryClick = { onCategorySelected(it) }
                    )
                }
            }
            if (isCurrentMonth) {
                pageData.recurringForecast?.takeIf { it.items.isNotEmpty() }?.let { forecast ->
                    item {
                        RecurringExpenseForecastCard(
                            forecast = forecast,
                            onTransactionClick = onForecastTransactionClick
                        )
                    }
                }
            }

            // ━━━ BLOCK 3: Spending Trend (누적 추이 차트) ━━━
            if (pageData.dailyCumulativeExpenses.isNotEmpty()) {
                item {
                    val trendInfo = HomeSpendingTrendInfo.from(pageData)
                    if (trendInfo != null) {
                        val targetModifier = if (isCurrentPage && coachMarkRegistry != null) {
                            Modifier.onboardingTarget("home_trend", coachMarkRegistry)
                        } else Modifier
                        Box(modifier = targetModifier) {
                            SpendingTrendSection(info = trendInfo)
                        }
                    }
                }
            }

            // ━━━ BLOCK 4: Category ━━━

            item {
                val targetModifier = if (isCurrentPage && coachMarkRegistry != null) {
                    Modifier.onboardingTarget("home_category", coachMarkRegistry)
                } else Modifier
                Box(modifier = targetModifier) {
                    CategoryExpenseSection(
                        categoryExpenses = pageData.categoryExpenses,
                        categoryBudgets = pageData.categoryBudgets,
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                }
            }

            // ━━━ BLOCK 5: Recent Transactions (오늘 내역 + 오늘 지출 요약) ━━━
            if (isCurrentMonth) {
                item {
                    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_today_transactions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.common_won,
                                    numberFormat.format(pageData.todayExpense)
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (pageData.todayExpenseCount > 0) {
                                Text(
                                    text = stringResource(R.string.home_today_count, pageData.todayExpenseCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                if (todayTransactions.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_no_today_transactions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(
                        count = todayTransactions.size,
                        key = { index ->
                            when (val item = todayTransactions[index]) {
                                is TodayItem.Expense -> "expense_${item.expense.id}"
                                is TodayItem.Income -> "income_${item.income.id}"
                            }
                        }
                    ) { index ->
                        when (val item = todayTransactions[index]) {
                            is TodayItem.Expense -> TransactionCardCompose(
                                info = ExpenseTransactionCardInfo(item.expense),
                                onLongClick = onTransactionLongClick?.let { open ->
                                    { open(TransactionTarget.Expense(item.expense.id)) }
                                },
                                onClick = { onExpenseSelected(item.expense) }
                            )
                            is TodayItem.Income -> TransactionCardCompose(
                                info = IncomeTransactionCardInfo(item.income),
                                onLongClick = onTransactionLongClick?.let { open ->
                                    { open(TransactionTarget.Income(item.income.id)) }
                                },
                                onClick = { onIncomeSelected(item.income) }
                            )
                        }
                    }
                }
            }
        }

        // Scroll to Top FAB
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.moneyTalkColors.income,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.common_scroll_to_top),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    } // Box
}

/** 오늘 내역 리스트에서 지출/수입을 통합 표현하기 위한 sealed interface */
sealed interface TodayItem {
    data class Expense(val expense: ExpenseEntity) : TodayItem
    data class Income(val income: IncomeEntity) : TodayItem
}
