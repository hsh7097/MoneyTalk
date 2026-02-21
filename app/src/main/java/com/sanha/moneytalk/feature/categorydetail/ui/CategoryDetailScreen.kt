package com.sanha.moneytalk.feature.categorydetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderCompose
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.categorydetail.ui.model.CategorySpendingTrendInfo
import com.sanha.moneytalk.feature.home.ui.component.SpendingTrendSection
import kotlinx.coroutines.launch

/**
 * 카테고리 상세 화면 메인 Composable.
 *
 * 상단 TopBar(뒤로가기 + 카테고리명) + HorizontalPager(월별 차트 + 거래 목록).
 * 거래 아이템 탭 시 ExpenseDetailDialog 표시.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    onBack: () -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 선택된 지출 항목 (상세보기용)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // HorizontalPager — Virtual Infinite Pager
    val initialPage = remember {
        MonthPagerUtils.yearMonthToPage(uiState.selectedYear, uiState.selectedMonth)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MonthPagerUtils.TOTAL_PAGE_COUNT }
    )
    val coroutineScope = rememberCoroutineScope()

    // ViewModel의 선택 월이 변경되면 Pager 위치 동기화
    LaunchedEffect(uiState.selectedYear, uiState.selectedMonth) {
        val selectedPage = MonthPagerUtils.yearMonthToPage(
            uiState.selectedYear, uiState.selectedMonth
        )
        if (pagerState.currentPage != selectedPage) {
            pagerState.scrollToPage(selectedPage)
        }
    }

    // 페이지 변경 시 ViewModel에 월 변경 통지
    LaunchedEffect(pagerState.currentPage) {
        val (year, month) = MonthPagerUtils.pageToYearMonth(pagerState.currentPage)
        viewModel.setMonth(year, month)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // TopBar: 뒤로가기 + 카테고리 이모지 + 이름
        TopAppBar(
            title = {
                Text(
                    text = "${uiState.categoryEmoji} ${uiState.categoryDisplayName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // HorizontalPager: 각 페이지 = 월별 콘텐츠
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it }
        ) { page ->
            val (pageYear, pageMonth) = remember(page) {
                MonthPagerUtils.pageToYearMonth(page)
            }
            val pageData = uiState.pageCache[MonthKey(pageYear, pageMonth)]
                ?: CategoryDetailPageData()

            CategoryDetailPageContent(
                pageData = pageData,
                year = pageYear,
                month = pageMonth,
                monthStartDay = uiState.monthStartDay,
                categoryName = uiState.categoryDisplayName,
                onPreviousMonth = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNextMonth = {
                    coroutineScope.launch {
                        val target = pagerState.currentPage + 1
                        if (!MonthPagerUtils.isFutureMonth(target)) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onExpenseSelected = { expense -> selectedExpense = expense }
            )
        }
    }

    // 지출 상세 다이얼로그
    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = {
                viewModel.deleteExpense(expense)
                selectedExpense = null
            },
            onCategoryChange = { newCategory ->
                viewModel.updateExpenseCategory(expense.storeName, newCategory)
                selectedExpense = null
            },
            onMemoChange = { memo ->
                viewModel.updateExpenseMemo(expense.id, memo)
                selectedExpense = null
            }
        )
    }
}

/**
 * 카테고리 상세 페이지 콘텐츠 (월별).
 *
 * 월 네비게이션 + 누적 추이 차트 + 거래 목록.
 */
@Composable
private fun CategoryDetailPageContent(
    pageData: CategoryDetailPageData,
    year: Int,
    month: Int,
    monthStartDay: Int,
    categoryName: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExpenseSelected: (ExpenseEntity) -> Unit
) {
    if (pageData.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 월 네비게이션 헤더
        item(key = "month_header") {
            MonthNavigationHeader(
                year = year,
                month = month,
                monthStartDay = monthStartDay,
                periodLabel = pageData.periodLabel,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        }

        // 누적 추이 차트
        item(key = "spending_trend") {
            val trendInfo = CategorySpendingTrendInfo.from(pageData, categoryName)
            if (trendInfo != null) {
                SpendingTrendSection(
                    info = trendInfo,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // 거래 목록
        if (pageData.transactionItems.isEmpty() && pageData.monthlyExpense == 0) {
            item(key = "empty") {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.category_detail_empty, categoryName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(
                items = pageData.transactionItems,
                key = { item ->
                    when (item) {
                        is CategoryTransactionItem.Header -> "header_${item.title}"
                        is CategoryTransactionItem.ExpenseItem -> "expense_${item.expense.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is CategoryTransactionItem.Header -> {
                        TransactionGroupHeaderCompose(info = item)
                    }
                    is CategoryTransactionItem.ExpenseItem -> {
                        TransactionCardCompose(
                            info = item.cardInfo,
                            onClick = { onExpenseSelected(item.expense) }
                        )
                    }
                }
            }
        }

        // 하단 여백
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 월 네비게이션 헤더.
 * 좌우 화살표 + 년월 표시 + 기간 레이블.
 */
@Composable
private fun MonthNavigationHeader(
    year: Int,
    month: Int,
    monthStartDay: Int,
    periodLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.home_previous_month)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = DateUtils.formatCustomYearMonth(year, month, monthStartDay),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (periodLabel.isNotBlank()) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        val isCurrentMonth = year >= DateUtils.getCurrentYear() &&
            month >= DateUtils.getCurrentMonth()
        IconButton(
            onClick = onNextMonth,
            enabled = !isCurrentMonth
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_next_month),
                tint = if (isCurrentMonth) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
