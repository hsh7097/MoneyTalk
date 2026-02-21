package com.sanha.moneytalk.feature.home.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.chart.DonutChartCompose
import com.sanha.moneytalk.core.ui.component.chart.DonutSlice
import com.sanha.moneytalk.feature.home.ui.component.SpendingTrendSection
import com.sanha.moneytalk.feature.home.ui.model.HomeSpendingTrendInfo
import com.sanha.moneytalk.core.ui.component.FullSyncCtaSection
import com.sanha.moneytalk.core.ui.component.getCategoryChartColor
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
/** 홈 탭 메인 화면. 월간 현황, 카테고리별 지출, 오늘 지출/전월 대비, 오늘 거래 내역을 표시 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    autoSyncOnStart: Boolean = false,
    onAutoSyncConsumed: () -> Unit = {},
    homeTabReClickEvent: kotlinx.coroutines.flow.SharedFlow<Unit>? = null
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 선택된 지출 항목 (상세보기용)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 앱 시작 시 자동 동기화
    LaunchedEffect(autoSyncOnStart) {
        if (autoSyncOnStart) {
            viewModel.syncIncremental(contentResolver)
            onAutoSyncConsumed()
        }
    }

    // 화면 재진입(resume) 시 데이터 새로고침 (AI 인사이트 포함)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Flow 기반 데이터 로딩: Room DB 변경 시 자동으로 UI 갱신됨
    // (다른 탭에서 카테고리 변경, 지출 삭제 등의 변경사항이 실시간 반영)

    // HorizontalPager — Virtual Infinite Pager (1200페이지, 중앙이 현재 월)
    val initialPage = remember {
        MonthPagerUtils.yearMonthToPage(uiState.selectedYear, uiState.selectedMonth)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MonthPagerUtils.TOTAL_PAGE_COUNT }
    )
    val coroutineScope = rememberCoroutineScope()

    // ViewModel의 선택 월이 외부 요인(예: DataStore 설정 로드)으로 변경되면 Pager 위치도 동기화
    LaunchedEffect(uiState.selectedYear, uiState.selectedMonth) {
        val selectedPage = MonthPagerUtils.yearMonthToPage(
            uiState.selectedYear,
            uiState.selectedMonth
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

    // 홈 탭 재클릭 → 오늘(현재 월) 페이지로 이동
    LaunchedEffect(homeTabReClickEvent) {
        homeTabReClickEvent?.collect {
            val todayPage = MonthPagerUtils.yearMonthToPage(
                DateUtils.getCurrentYear(),
                DateUtils.getCurrentMonth()
            )
            if (pagerState.currentPage != todayPage) {
                pagerState.animateScrollToPage(todayPage)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
        // 이 페이지의 (year, month) 계산
        val (pageYear, pageMonth) = remember(page) {
            MonthPagerUtils.pageToYearMonth(page)
        }
        // pageCache에서 이 페이지의 데이터 읽기 (없으면 기본값)
        val pageData = uiState.pageCache[MonthKey(pageYear, pageMonth)]
            ?: HomePageData()

        HomePageContent(
            pageData = pageData,
            year = pageYear,
            month = pageMonth,
            monthStartDay = uiState.monthStartDay,
            isSyncing = uiState.isSyncing,
            isMonthSynced = viewModel.isMonthSynced(pageYear, pageMonth),
            isPartiallyCovered = viewModel.isPagePartiallyCovered(pageYear, pageMonth),
            selectedCategory = uiState.selectedCategory,
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
            onIncrementalSync = {
                onRequestSmsPermission {
                    viewModel.syncIncremental(contentResolver)
                }
            },
            onFullSync = {
                if (viewModel.isMonthSynced(pageYear, pageMonth)) {
                    // 이미 해제됨 → 현재 보고 있는 월만 동기화
                    onRequestSmsPermission {
                        viewModel.syncMonthData(contentResolver, pageYear, pageMonth)
                    }
                } else {
                    // 미해제 → 광고 다이얼로그 표시
                    viewModel.preloadFullSyncAd()
                    viewModel.showFullSyncAdDialog()
                }
            },
            onCategorySelected = { category ->
                if (category != null) {
                    val intent = android.content.Intent(
                        context,
                        com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailActivity::class.java
                    ).apply {
                        putExtra(
                            com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailActivity.EXTRA_CATEGORY,
                            category
                        )
                        putExtra(
                            com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailActivity.EXTRA_YEAR,
                            uiState.selectedYear
                        )
                        putExtra(
                            com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailActivity.EXTRA_MONTH,
                            uiState.selectedMonth
                        )
                    }
                    context.startActivity(intent)
                }
            },
            onExpenseSelected = { expense -> selectedExpense = expense },
            coroutineScope = coroutineScope
        )
    } // HorizontalPager

    // 지출 상세 다이얼로그 (공통 컴포넌트 사용)
    selectedExpense?.let { expense ->
        Log.e("MT_DEBUG", "HomeScreen[selectedExpense] : ${expense.storeName}, ${expense.amount}원")
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = {
                viewModel.deleteExpense(expense)
                selectedExpense = null
            },
            onCategoryChange = { newCategory ->
                viewModel.updateExpenseCategory(
                    storeName = expense.storeName,
                    newCategory = newCategory
                )
                selectedExpense = null
            },
            onMemoChange = { memo ->
                viewModel.updateExpenseMemo(expense.id, memo)
                selectedExpense = null
            }
        )
    }

    // 에러 메시지 스낵바
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            // 3초 후 에러 메시지 클리어
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    // 카테고리 분류 확인 다이얼로그
    if (uiState.showClassifyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClassifyDialog() },
            title = { Text(stringResource(R.string.classify_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.classify_dialog_message,
                        uiState.unclassifiedCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.startFullClassification() }) {
                    Text(stringResource(R.string.classify_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClassifyDialog() }) {
                    Text(stringResource(R.string.classify_dialog_later))
                }
            }
        )
    }

    // 분류 진행 중 다이얼로그
    if (uiState.isClassifying) {
        AlertDialog(
            onDismissRequest = { /* 진행 중에는 닫기 불가 */ },
            title = { Text(stringResource(R.string.classify_progress_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 진행률 바 (총 진행률)
                    if (uiState.classifyProgressTotal > 0) {
                        val progress =
                            uiState.classifyProgressCurrent.toFloat() / uiState.classifyProgressTotal.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.classifyProgressCurrent} / ${uiState.classifyProgressTotal}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = uiState.classifyProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { }
        )
    }

    // SMS 동기화 진행 다이얼로그
    if (uiState.showSyncDialog) {
        AlertDialog(
            onDismissRequest = { /* 진행 중에는 닫기 불가 */ },
            title = { Text(stringResource(R.string.home_sync_dialog_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 로딩 스피너
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 진행률 바 (total > 0일 때만 determinate)
                    if (uiState.syncProgressTotal > 0) {
                        val progress =
                            uiState.syncProgressCurrent.toFloat() / uiState.syncProgressTotal.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.syncProgressCurrent} / ${uiState.syncProgressTotal}건",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 상태 텍스트
                    Text(
                        text = uiState.syncProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { }
        )
    }

    // 전체 동기화 해제 광고 다이얼로그
    if (uiState.showFullSyncAdDialog) {
        val activity = context as? android.app.Activity
        val isCurrentMonthForDialog = uiState.selectedYear == DateUtils.getCurrentYear() &&
                uiState.selectedMonth == DateUtils.getCurrentMonth()
        val dialogMonthLabel = if (isCurrentMonthForDialog) "이번달" else "${uiState.selectedMonth}월"
        AlertDialog(
            onDismissRequest = { viewModel.dismissFullSyncAdDialog() },
            title = { Text(stringResource(R.string.full_sync_ad_dialog_title, dialogMonthLabel)) },
            text = { Text(stringResource(R.string.full_sync_ad_dialog_message, dialogMonthLabel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activity != null) {
                            viewModel.dismissFullSyncAdDialog()
                            viewModel.adManager.showAd(
                                activity = activity,
                                onRewarded = {
                                    onRequestSmsPermission {
                                        viewModel.unlockFullSync(contentResolver)
                                    }
                                },
                                onFailed = {
                                    // 광고 로드/표시 실패는 앱/광고 이슈 → 유저 책임 아님 → 보상 처리
                                    onRequestSmsPermission {
                                        viewModel.unlockFullSync(contentResolver)
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.full_sync_ad_watch_button, dialogMonthLabel))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFullSyncAdDialog() }) {
                    Text(stringResource(R.string.full_sync_ad_later))
                }
            }
        )
    }
}

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
    isSyncing: Boolean,
    isMonthSynced: Boolean,
    isPartiallyCovered: Boolean,
    selectedCategory: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncrementalSync: () -> Unit,
    onFullSync: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onExpenseSelected: (ExpenseEntity) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 월간 현황
            item {
                MonthlyOverviewSection(
                    year = year,
                    month = month,
                    monthStartDay = monthStartDay,
                    periodLabel = pageData.periodLabel,
                    income = pageData.monthlyIncome,
                    expense = pageData.monthlyExpense,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onIncrementalSync = onIncrementalSync,
                    onFullSync = onFullSync,
                    isSyncing = isSyncing
                )
            }

            // 누적 지출 추이 차트 (월간 현황 바로 아래)
            if (pageData.dailyCumulativeExpenses.isNotEmpty()) {
                item {
                    val trendInfo = HomeSpendingTrendInfo.from(pageData)
                    if (trendInfo != null) {
                        SpendingTrendSection(info = trendInfo)
                    }
                }
            }

            // 데이터 0건 + 현재 월 아님 + 전체 동기화 미해제 → 빈 CTA 표시
            val isCurrentMonth = year == DateUtils.getCurrentYear() && month == DateUtils.getCurrentMonth()
            val hasNoData = !pageData.isLoading &&
                    pageData.monthlyExpense == 0 && pageData.monthlyIncome == 0
            val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced
            // 데이터 있지만 부분 커버 + 해당 월 미동기화 → 부분 CTA 표시
            val showPartialCta = !hasNoData && isPartiallyCovered && !isMonthSynced
            // 월 라벨: 현재 월이면 "이번달", 아니면 "M월"
            val ctaMonthLabel = if (isCurrentMonth) "이번달" else "${month}월"

            if (showEmptyCta) {
                item {
                    FullSyncCtaSection(
                        onRequestFullSync = onFullSync,
                        monthLabel = ctaMonthLabel
                    )
                }
            } else {
                // 부분 데이터 안내 CTA
                if (showPartialCta) {
                    item {
                        FullSyncCtaSection(
                            onRequestFullSync = onFullSync,
                            monthLabel = ctaMonthLabel,
                            isPartial = true
                        )
                    }
                }

                // 카테고리별 지출
                item {
                    CategoryExpenseSection(
                        categoryExpenses = pageData.categoryExpenses,
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                }

                // AI 인사이트
                if (pageData.aiInsight.isNotBlank()) {
                    item {
                        AiInsightCard(
                            insight = pageData.aiInsight,
                            monthlyExpense = pageData.monthlyExpense,
                            lastMonthExpense = pageData.lastMonthExpense
                        )
                    }
                }

                // 오늘의 지출 + 전월 대비 (당월에서만 표시)
                if (isCurrentMonth) {
                    item {
                        TodayAndComparisonSection(
                            todayExpense = pageData.todayExpense,
                            todayExpenseCount = pageData.todayExpenseCount,
                            monthlyExpense = pageData.monthlyExpense,
                            lastMonthExpense = pageData.lastMonthExpense,
                            comparisonPeriodLabel = pageData.comparisonPeriodLabel
                        )
                    }

                    // 오늘 내역 (지출 + 수입 시간순 통합)
                    item {
                        Text(
                            text = stringResource(R.string.home_today_transactions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (todayTransactions.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.home_no_today_transactions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                                    onClick = { onExpenseSelected(item.expense) }
                                )
                                is TodayItem.Income -> TransactionCardCompose(
                                    info = IncomeTransactionCardInfo(item.income),
                                    onClick = { }
                                )
                            }
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

/** 월간 수입/지출 현황 섹션. 월 네비게이션, 총 수입·지출 금액, 월별 잔액을 표시 */
@Composable
fun MonthlyOverviewSection(
    year: Int,
    month: Int,
    monthStartDay: Int,
    periodLabel: String,
    income: Int,
    expense: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncrementalSync: () -> Unit,
    onFullSync: () -> Unit,
    isSyncing: Boolean
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    var showSyncMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 월 선택기
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = DateUtils.formatCustomYearMonth(year, month, monthStartDay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
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

            Row {
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

                // 동기화 버튼 + 드롭다운 메뉴
                Box {
                    IconButton(
                        onClick = { showSyncMenu = true },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.home_sync)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSyncMenu,
                        onDismissRequest = { showSyncMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_sync_new_only)) },
                            onClick = {
                                showSyncMenu = false
                                onIncrementalSync()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_sync_full)) },
                            onClick = {
                                showSyncMenu = false
                                onFullSync()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 이번 달 지출 (중앙 대형 표시)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_this_month_expense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₩${numberFormat.format(expense)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 수입 뱃지
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = MaterialTheme.moneyTalkColors.income,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_income_badge, numberFormat.format(income)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 카테고리별 지출 비율 섹션. 상위 카테고리 막대 그래프 + 전체 목록을 표시 */
@Composable
fun CategoryExpenseSection(
    categoryExpenses: List<CategorySum>,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {}
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    var showAll by remember { mutableStateOf(false) }
    val othersLabel = stringResource(R.string.home_chart_others_label)

    // 금액 내림차순 정렬 ("AI 분류 중" 항목은 항상 마지막에 배치)
    val mergedExpenses = remember(categoryExpenses) {
        val classified = categoryExpenses
            .filter { it.category != "미분류" }
            .sortedByDescending { it.total }
        val unclassifiedTotal = categoryExpenses
            .filter { it.category == "미분류" }
            .sumOf { it.total }
        if (unclassifiedTotal > 0) {
            classified + CategorySum(Category.UNCLASSIFIED.displayName, unclassifiedTotal)
        } else {
            classified
        }
    }

    val totalExpense = remember(mergedExpenses) {
        mergedExpenses.sumOf { it.total }
    }

    // TOP 3 또는 전체 표시
    val displayList = remember(mergedExpenses, showAll) {
        if (showAll) mergedExpenses else mergedExpenses.take(3)
    }

    // 도넛 차트용 슬라이스 데이터 — showAll 상태에 따라 도넛도 연동
    // 기본: TOP3 + "그 외" 4조각 (범례와 정합), 전체보기: 전체 카테고리
    val chartSlices = remember(mergedExpenses, totalExpense, showAll) {
        if (showAll || mergedExpenses.size <= 3) {
            // 전체 카테고리 도넛
            mergedExpenses.map { item ->
                val category = Category.fromDisplayName(item.category)
                DonutSlice(
                    category = category,
                    amount = item.total,
                    percentage = if (totalExpense > 0) item.total.toFloat() / totalExpense else 0f,
                    color = getCategoryChartColor(category)
                )
            }
        } else {
            // TOP3 + "그 외" 합산 = 4조각
            val top3 = mergedExpenses.take(3).map { item ->
                val category = Category.fromDisplayName(item.category)
                DonutSlice(
                    category = category,
                    amount = item.total,
                    percentage = if (totalExpense > 0) item.total.toFloat() / totalExpense else 0f,
                    color = getCategoryChartColor(category)
                )
            }
            val othersTotal = mergedExpenses.drop(3).sumOf { it.total }
            if (othersTotal > 0) {
                top3 + DonutSlice(
                    category = Category.ETC,
                    amount = othersTotal,
                    percentage = if (totalExpense > 0) othersTotal.toFloat() / totalExpense else 0f,
                    color = Color(0xFFBDBDBD),
                    displayLabel = othersLabel
                )
            } else {
                top3
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 헤더: "카테고리별 지출" + "전체보기/접기"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_category_expense),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (mergedExpenses.size > 3) {
                Text(
                    text = if (showAll) stringResource(R.string.home_view_collapse)
                    else stringResource(R.string.home_view_all),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showAll = !showAll }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayList.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_expense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            // 카테고리가 있으면 도넛 차트 표시
            if (mergedExpenses.isNotEmpty()) {
                DonutChartCompose(
                    slices = chartSlices,
                    totalAmount = totalExpense
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            displayList.forEach { item ->
                val category = Category.fromDisplayName(item.category)
                val chartColor = getCategoryChartColor(category)
                val percentage = if (totalExpense > 0) {
                    (item.total.toFloat() / totalExpense * 100).toInt()
                } else 0
                val progress = if (totalExpense > 0) {
                    item.total.toFloat() / totalExpense
                } else 0f
                val isSelected = selectedCategory == item.category

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) onCategorySelected(null)
                            else onCategorySelected(item.category)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // 카테고리명 + 금액/퍼센트
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = category.emoji,
                                fontSize = 20.sp
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "₩${numberFormat.format(item.total)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${percentage}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 프로그레스 바 (카테고리별 고유 색상 — 도넛 차트와 동기화)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(chartColor)
                        )
                    }
                }
            }
        }
    }
}


/** 오늘의 지출 카드와 전월 대비 카드를 가로 2분할 배치하는 래퍼 섹션 */
@Composable
fun TodayAndComparisonSection(
    todayExpense: Int,
    todayExpenseCount: Int,
    monthlyExpense: Int,
    lastMonthExpense: Int,
    comparisonPeriodLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TodayExpenseCard(
            todayExpense = todayExpense,
            todayExpenseCount = todayExpenseCount,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        MonthComparisonCard(
            monthlyExpense = monthlyExpense,
            lastMonthExpense = lastMonthExpense,
            comparisonPeriodLabel = comparisonPeriodLabel,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

/** 오늘의 지출 카드. 오늘 총 지출 금액과 건수를 표시 */
@Composable
fun TodayExpenseCard(
    todayExpense: Int,
    todayExpenseCount: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.home_today_expense),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₩${numberFormat.format(todayExpense)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (todayExpenseCount > 0) stringResource(R.string.home_today_count, todayExpenseCount) else " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/** 전월 대비 카드. 이번 달과 지난달 동일 기간의 지출 차이를 표시 */
@Composable
fun MonthComparisonCard(
    monthlyExpense: Int,
    lastMonthExpense: Int,
    comparisonPeriodLabel: String,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val diff = monthlyExpense - lastMonthExpense
    val comparisonValue = when {
        lastMonthExpense == 0 -> "-"
        diff > 0 -> "+₩${numberFormat.format(diff)}"
        diff < 0 -> "-₩${numberFormat.format(-diff)}"
        else -> "₩0"
    }
    val comparisonSub = when {
        lastMonthExpense == 0 -> stringResource(R.string.home_vs_last_month_no_data)
        diff > 0 -> stringResource(R.string.home_vs_last_month_more, numberFormat.format(diff))
        diff < 0 -> stringResource(R.string.home_vs_last_month_less, numberFormat.format(-diff))
        else -> stringResource(R.string.home_vs_last_month_same)
    }
    val comparisonValueColor = when {
        lastMonthExpense == 0 -> MaterialTheme.colorScheme.onSurface
        diff > 0 -> MaterialTheme.colorScheme.error
        diff < 0 -> MaterialTheme.moneyTalkColors.income
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_vs_last_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor
                )
                if (comparisonPeriodLabel.isNotBlank()) {
                    Text(
                        text = "($comparisonPeriodLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comparisonValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = comparisonValueColor
            )
            Text(
                text = comparisonSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/** 지출 내역이 없을 때 표시하는 빈 상태 섹션 */
@Composable
fun EmptyExpenseSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\uD83D\uDCED",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_expense_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.home_empty_expense_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** AI 인사이트 카드. Gemini가 생성한 이번 달 소비 분석 요약 + 전월 대비 절대금액 표시 */
@Composable
fun AiInsightCard(
    insight: String,
    monthlyExpense: Int = 0,
    lastMonthExpense: Int = 0
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val diff = monthlyExpense - lastMonthExpense

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 전월 대비 절대금액 변화 (전월 데이터 있을 때만)
            if (lastMonthExpense > 0 && diff != 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (diff > 0) {
                        stringResource(R.string.home_insight_diff_more, numberFormat.format(diff))
                    } else {
                        stringResource(R.string.home_insight_diff_less, numberFormat.format(-diff))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (diff > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.moneyTalkColors.income
                    }
                )
            }
        }
    }
}

/** 오늘 내역 리스트에서 지출/수입을 통합 표현하기 위한 sealed interface */
sealed interface TodayItem {
    data class Expense(val expense: ExpenseEntity) : TodayItem
    data class Income(val income: IncomeEntity) : TodayItem
}
