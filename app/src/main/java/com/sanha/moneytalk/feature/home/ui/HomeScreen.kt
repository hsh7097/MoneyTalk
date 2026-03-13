package com.sanha.moneytalk.feature.home.ui

import androidx.activity.ComponentActivity
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.MainViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.BannerAdCompose
import com.sanha.moneytalk.core.ui.component.BannerAdIds
import com.sanha.moneytalk.core.ui.component.FullSyncCtaSection
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.feature.history.ui.IncomeDetailDialog
import com.sanha.moneytalk.feature.home.ui.component.ImportDataCtaSection
import com.sanha.moneytalk.feature.home.ui.component.SpendingTrendSection
import com.sanha.moneytalk.feature.home.ui.model.HomeSpendingTrendInfo
import com.sanha.moneytalk.core.ui.component.getCustomCategoryBackgroundColor
import com.sanha.moneytalk.core.ui.component.getCustomCategoryChartColor
import com.sanha.moneytalk.core.ui.component.getCategoryChartColor
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.ui.coachmark.homeCoachMarkSteps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
/** 홈 탭 메인 화면. 월간 현황, 카테고리별 지출, 오늘 지출/전월 대비, 오늘 거래 내역을 표시 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    homeTabReClickEvent: kotlinx.coroutines.flow.SharedFlow<Unit>? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Activity-scoped MainViewModel (동기화/권한/광고 상태 참조)
    val mainViewModel: MainViewModel = hiltViewModel(
        viewModelStoreOwner = context as ComponentActivity
    )
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    // 선택된 지출/수입 항목 (상세보기용)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var selectedIncome by remember { mutableStateOf<IncomeEntity?>(null) }

    // HorizontalPager — Virtual Infinite Pager (1200페이지, 중앙이 현재 월)
    val initialPage = remember {
        MonthPagerUtils.yearMonthToPage(uiState.selectedYear, uiState.selectedMonth)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MonthPagerUtils.getPageCount(uiState.monthStartDay) }
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

    // 홈 탭 재클릭 → 오늘(현재 커스텀 월) 페이지로 이동
    val currentMonthStartDay by rememberUpdatedState(uiState.monthStartDay)
    LaunchedEffect(homeTabReClickEvent) {
        homeTabReClickEvent?.collect {
            val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(currentMonthStartDay)
            val todayPage = MonthPagerUtils.yearMonthToPage(effYear, effMonth)
            if (pagerState.currentPage != todayPage) {
                pagerState.animateScrollToPage(todayPage)
            }
        }
    }

    val isBannerAdEnabled by mainViewModel.adManager.isBannerAdEnabledFlow
        .collectAsStateWithLifecycle(initialValue = false)

    // ===== 코치마크 (화면별 온보딩) =====
    val coachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val coachMarkState = remember { CoachMarkState() }
    val allHomeSteps = remember { homeCoachMarkSteps() }
    val hasSeenHomeOnboarding by viewModel.hasSeenScreenOnboardingFlow("home")
        .collectAsStateWithLifecycle(initialValue = true)

    // 현재 페이지 데이터 로딩 완료 + 데이터 존재 시 코치마크 표시
    val currentMonthKey = MonthKey(uiState.selectedYear, uiState.selectedMonth)
    val currentPageData = uiState.pageCache[currentMonthKey]
    val isCurrentPageLoading = currentPageData?.isLoading ?: true
    val hasData = (currentPageData?.monthlyExpense ?: 0) > 0 ||
        (currentPageData?.monthlyIncome ?: 0) > 0

    LaunchedEffect(hasSeenHomeOnboarding, isCurrentPageLoading, hasData) {
        if (!hasSeenHomeOnboarding && !isCurrentPageLoading && hasData) {
            delay(500) // 레이아웃 안정화 대기
            val visibleSteps = allHomeSteps.filter { it.targetKey in coachMarkRegistry.targets }
            if (visibleSteps.isNotEmpty()) {
                coachMarkState.show(visibleSteps)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { it }
        ) { page ->
            // 이 페이지의 (year, month) 계산
            val (pageYear, pageMonth) = remember(page) {
                MonthPagerUtils.pageToYearMonth(page)
            }
            // pageCache에서 이 페이지의 데이터 읽기
            // 캐시 미적재 페이지는 isLoading=false로 처리하여 CTA 조건이 즉시 평가되도록 함
            val pageData = uiState.pageCache[MonthKey(pageYear, pageMonth)]
                ?: HomePageData(isLoading = false)

            HomePageContent(
                pageData = pageData,
                year = pageYear,
                month = pageMonth,
                monthStartDay = uiState.monthStartDay,
                isMonthSynced = mainViewModel.isMonthSynced(pageYear, pageMonth),
                isPartiallyCovered = mainViewModel.isPagePartiallyCovered(pageYear, pageMonth),
                hasSmsPermission = mainUiState.hasSmsPermission,
                selectedCategory = uiState.selectedCategory,
                isSyncing = mainUiState.isSyncing,
                isAdEnabled = isBannerAdEnabled && !mainUiState.hasFreeSyncRemaining,
                onPreviousMonth = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNextMonth = {
                    coroutineScope.launch {
                        val target = pagerState.currentPage + 1
                        if (!MonthPagerUtils.isFutureMonth(target, uiState.monthStartDay)) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onIncrementalSync = {
                    onRequestSmsPermission {
                        mainViewModel.syncIncremental()
                    }
                },
                onFullSync = {
                    val (effY, effM) = DateUtils.getEffectiveCurrentMonth(uiState.monthStartDay)
                    val isPageCurrentMonth = pageYear == effY && pageMonth == effM
                    if (mainViewModel.isMonthSynced(pageYear, pageMonth) || isPageCurrentMonth) {
                        // 이미 동기화됨 / 현재월 → 바로 동기화
                        onRequestSmsPermission {
                            mainViewModel.syncMonthData(pageYear, pageMonth)
                        }
                    } else if (!isBannerAdEnabled) {
                        // 광고 비활성 → 광고 없이 바로 전체 동기화 해제
                        onRequestSmsPermission {
                            mainViewModel.unlockFullSync(pageYear, pageMonth)
                        }
                    } else if (mainUiState.hasFreeSyncRemaining) {
                        // 무료 동기화 잔여 횟수 있음 → 광고 없이 동기화
                        onRequestSmsPermission {
                            mainViewModel.unlockFullSync(pageYear, pageMonth, isFreeSyncUsed = true)
                        }
                    } else {
                        mainViewModel.preloadFullSyncAd()
                        mainViewModel.showFullSyncAdDialog(pageYear, pageMonth)
                    }
                },
                onCategorySelected = { category ->
                    if (category != null) {
                        com.sanha.moneytalk.feature.categorydetail.CategoryDetailActivity.open(
                            context,
                            category,
                            uiState.selectedYear,
                            uiState.selectedMonth
                        )
                    }
                },
                onExpenseSelected = { expense -> selectedExpense = expense },
                onIncomeSelected = { income -> selectedIncome = income },
                coachMarkRegistry = coachMarkRegistry,
                isCurrentPage = page == pagerState.currentPage,
                coroutineScope = coroutineScope
            )
        } // HorizontalPager

        // 배너 광고 (RTDB reward_ad_enabled 연동)
        if (isBannerAdEnabled) {
            BannerAdCompose(adUnitId = BannerAdIds.HOME)
        }
    } // Column

    // 코치마크 오버레이 (Column 위에 렌더링)
    CoachMarkOverlay(
        state = coachMarkState,
        targetRegistry = coachMarkRegistry,
        onComplete = { viewModel.markScreenOnboardingSeen("home") }
    )
    } // Box

    // 지출 상세 다이얼로그 (공통 컴포넌트 사용)
    selectedExpense?.let { expense ->
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

    // 수입 상세 다이얼로그
    selectedIncome?.let { income ->
        IncomeDetailDialog(
            income = income,
            onDismiss = { selectedIncome = null },
            onDelete = {
                viewModel.deleteIncome(income)
                selectedIncome = null
            },
            onMemoChange = { memo ->
                viewModel.updateIncomeMemo(income.id, memo)
                selectedIncome = null
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

    // 동기화 다이얼로그, AI 성과 요약, 전체 동기화 광고 다이얼로그는
    // Activity 레벨(MoneyTalkApp)에서 MainViewModel을 통해 표시
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ━━━ BLOCK 1: Hero Summary ━━━
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

            // CTA 표시 조건 계산
            val (effYearCta, effMonthCta) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
            val isCurrentMonth = year == effYearCta && month == effMonthCta
            val hasNoData = !pageData.isLoading &&
                    pageData.monthlyExpense == 0 && pageData.monthlyIncome == 0

            // 데이터 가져오기 CTA: 현재월 + 권한 없거나 데이터 없음
            val showImportCta = isCurrentMonth && (!hasSmsPermission || hasNoData)

            // 현재월 데이터 가져오기 CTA (권한 없거나 데이터 없을 때)
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

            // 과거 월 전체 동기화 CTA (광고 시청 → 데이터 가져오기)
            val ctaMonthLabel = if (isCurrentMonth) "이번달" else "${month}월"
            val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced
            val showPartialCta = !hasNoData && !isCurrentMonth && isPartiallyCovered && !isMonthSynced

            if (showEmptyCta) {
                item {
                    FullSyncCtaSection(
                        onRequestFullSync = onFullSync,
                        monthLabel = ctaMonthLabel,
                        isSyncing = isSyncing,
                        isAdEnabled = isAdEnabled
                    )
                }
            }

            // ━━━ BLOCK 2: Spending Trend (누적 추이 차트) ━━━
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

            // 부분 데이터 안내 CTA (데이터 있지만 해당 월 미동기화)
            if (showPartialCta) {
                item {
                    FullSyncCtaSection(
                        onRequestFullSync = onFullSync,
                        monthLabel = ctaMonthLabel,
                        isPartial = true,
                        isSyncing = isSyncing,
                        isAdEnabled = isAdEnabled
                    )
                }
            }

            // ━━━ BLOCK 3: Category + AI Insight ━━━
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

            // AI 인사이트 (카테고리 바로 아래)
            if (pageData.aiInsight.isNotBlank()) {
                item {
                    AiInsightCard(
                        insight = pageData.aiInsight,
                        monthlyExpense = pageData.monthlyExpense,
                        lastMonthExpense = pageData.lastMonthExpense
                    )
                }
            }

            // ━━━ BLOCK 4: Recent Transactions (오늘 내역 + 오늘 지출 요약) ━━━
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
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "₩${numberFormat.format(pageData.todayExpense)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pageData.todayExpenseCount > 0) {
                                Text(
                                    text = stringResource(R.string.home_today_count, pageData.todayExpenseCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onClick = { onExpenseSelected(item.expense) }
                            )
                            is TodayItem.Income -> TransactionCardCompose(
                                info = IncomeTransactionCardInfo(item.income),
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

/** 월간 현황 히어로 섹션. 월 네비게이션(큰 화살표, 중앙 정렬) + Navy 카드 안에 총 지출/수입 뱃지 */
@Composable
fun MonthlyOverviewSection(
    year: Int,
    month: Int,
    monthStartDay: Int,
    periodLabel: String,
    income: Int,
    expense: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
    val isCurrentMonth = year > effYear || (year == effYear && month >= effMonth)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 월 네비게이션 — 큰 화살표 + 중앙 정렬
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_previous_month),
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = DateUtils.formatCustomYearMonth(year, month, monthStartDay),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (periodLabel.isNotBlank()) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            IconButton(
                onClick = onNextMonth,
                enabled = !isCurrentMonth,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    modifier = Modifier.size(32.dp),
                    tint = if (isCurrentMonth) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero 카드: Navy 그라데이션 배경 + 총 지출 + 수입 뱃지
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_this_month_expense),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "₩${numberFormat.format(expense)}",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 수입 뱃지
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_income_badge, numberFormat.format(income)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/** 카테고리별 지출 비율 섹션. 수평 바 차트로 각 카테고리 비율을 표시 */
@Composable
fun CategoryExpenseSection(
    categoryExpenses: List<CategorySum>,
    categoryBudgets: Map<String, Int> = emptyMap(),
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {}
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    var showAll by remember { mutableStateOf(false) }

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

    // TOP 5 또는 전체 표시
    val displayList = remember(mergedExpenses, showAll) {
        if (showAll) mergedExpenses else mergedExpenses.take(5)
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
            if (mergedExpenses.size > 5) {
                Text(
                    text = if (showAll) stringResource(R.string.home_view_collapse)
                    else stringResource(R.string.home_view_all),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showAll = !showAll }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayList.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_expense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            displayList.forEachIndexed { index, item ->
                val category = Category.fromDisplayName(item.category)
                val categoryEmoji = rememberCategoryEmoji(item.category)
                val isCustomCategory = category == Category.ETC
                        && item.category != Category.ETC.displayName
                val budget = categoryBudgets[item.category]
                val budgetAmount = budget ?: 0
                val hasBudget = budget != null && budgetAmount > 0
                val isOverBudget = hasBudget && item.total > budgetAmount
                val budgetRatio = if (hasBudget) item.total.toFloat() / budgetAmount else 0f
                val isWarningBudget = hasBudget && budgetRatio in 0.9f..1f
                val isSelected = selectedCategory == item.category
                val isFirst = index == 0

                val percentage = if (hasBudget) {
                    (item.total.toFloat() / budgetAmount * 100).toInt()
                } else if (totalExpense > 0) {
                    (item.total.toFloat() / totalExpense * 100).toInt()
                } else 0

                val progress = if (hasBudget) {
                    (item.total.toFloat() / budgetAmount).coerceAtMost(1f)
                } else if (totalExpense > 0) {
                    item.total.toFloat() / totalExpense
                } else 0f

                // 예산 상태별 색상: 초과(빨강), 경고(주황), 기본
                val warningColor = MaterialTheme.moneyTalkColors.calendarSunday
                val barColor = when {
                    isOverBudget -> MaterialTheme.colorScheme.error
                    isWarningBudget -> warningColor
                    isFirst && !hasBudget -> MaterialTheme.colorScheme.primary
                    isCustomCategory -> getCustomCategoryChartColor(item.category)
                    else -> getCategoryChartColor(category)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) onCategorySelected(null)
                            else onCategorySelected(item.category)
                        }
                        .padding(vertical = 6.dp)
                ) {
                    // 상단: 아이콘 + 이름 | 금액 + 퍼센트
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 왼쪽: 원형 아이콘 + 카테고리명
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryIcon(
                                category = category,
                                emojiOverride = categoryEmoji,
                                backgroundColorOverride = if (isCustomCategory)
                                    getCustomCategoryBackgroundColor(item.category) else null,
                                containerSize = 40.dp,
                                fontSize = 20.sp
                            )
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 오른쪽: 금액 + 퍼센트 (한 줄)
                        if (hasBudget) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₩${numberFormat.format(item.total)} / ₩${numberFormat.format(budgetAmount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isOverBudget) {
                                        "₩${numberFormat.format(item.total - budgetAmount)} 초과"
                                    } else if (isWarningBudget) {
                                        "₩${numberFormat.format(budgetAmount - item.total)} 남음 ⚠️"
                                    } else {
                                        "₩${numberFormat.format(budgetAmount - item.total)} 남음"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isOverBudget -> MaterialTheme.colorScheme.error
                                        isWarningBudget -> warningColor
                                        else -> MaterialTheme.moneyTalkColors.income
                                    }
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "₩${numberFormat.format(item.total)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${percentage}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 수평 바 차트
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
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
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 전월 대비 절대금액 변화 (전월 데이터 있을 때만)
            if (lastMonthExpense > 0 && diff != 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(8.dp))
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

