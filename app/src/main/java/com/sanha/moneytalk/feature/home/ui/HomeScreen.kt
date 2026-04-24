package com.sanha.moneytalk.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.MainViewModel
import com.sanha.moneytalk.ScreenSyncUiState
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.BannerAdCompose
import com.sanha.moneytalk.core.ui.component.BannerAdIds
import com.sanha.moneytalk.core.ui.component.cta.FullSyncCtaSection
import com.sanha.moneytalk.core.ui.component.cta.ImportDataCtaSection
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
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.ui.coachmark.homeCoachMarkSteps
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
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
    val mainScreenUiState by mainViewModel.screenSyncUiState
        .collectAsStateWithLifecycle(initialValue = ScreenSyncUiState())

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
            delay(1000) // 레이아웃 안정화 + 화면 인지 여유
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
            // 캐시 미적재 첫 프레임은 loading 상태로 유지해 빈 CTA가 먼저 번쩍이지 않도록 한다.
            val pageData = uiState.pageCache[MonthKey(pageYear, pageMonth)]
                ?: HomePageData(isLoading = true)

            HomePageContent(
                pageData = pageData,
                year = pageYear,
                month = pageMonth,
                monthStartDay = uiState.monthStartDay,
                isMonthSynced = mainViewModel.isMonthSynced(pageYear, pageMonth),
                isPartiallyCovered = mainViewModel.isPagePartiallyCovered(pageYear, pageMonth),
                hasSmsPermission = mainScreenUiState.hasSmsPermission,
                selectedCategory = uiState.selectedCategory,
                isSyncing = mainScreenUiState.isSyncing,
                isAdEnabled = isBannerAdEnabled && !mainScreenUiState.hasFreeSyncRemaining,
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
                        val (effY, effM) = DateUtils.getEffectiveCurrentMonth(uiState.monthStartDay)
                        val isPageCurrentMonth = pageYear == effY && pageMonth == effM
                        if (isPageCurrentMonth) {
                            mainViewModel.syncMonthData(pageYear, pageMonth)
                        } else {
                            mainViewModel.syncIncremental()
                        }
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
                    } else if (mainScreenUiState.hasFreeSyncRemaining) {
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
                        com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailActivity.open(
                            context,
                            category,
                            uiState.selectedYear,
                            uiState.selectedMonth
                        )
                    }
                },
                onExpenseSelected = { expense ->
                    TransactionEditActivity.open(context, expenseId = expense.id)
                },
                onIncomeSelected = { income ->
                    TransactionEditActivity.open(context, incomeId = income.id)
                },
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

            // 과거 월 전체 동기화 CTA (광고 시청 → 데이터 가져오기)
            val ctaMonthLabel = if (isCurrentMonth) {
                currentMonthSyncLabel
            } else {
                String.format(syncMonthLabelFormat, month)
            }
            val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced
            val showPartialCta = !showImportCta &&
                    !hasNoData &&
                    !isCurrentMonth &&
                    isPartiallyCovered &&
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

            if (showPartialCta) {
                item {
                    FullSyncCtaSection(
                        onRequestFullSync = onFullSync,
                        monthLabel = ctaMonthLabel,
                        isPartial = true,
                        isSyncing = isSyncing,
                        isAdEnabled = !isCurrentMonth && isAdEnabled
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

            // ━━━ BLOCK 4: AI Insight + Category ━━━
            if (pageData.aiInsight.isNotBlank()) {
                item {
                    AiInsightCard(
                        insight = pageData.aiInsight
                    )
                }
            }

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
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.common_won,
                                    numberFormat.format(pageData.todayExpense)
                                ),
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

        // Hero 카드: 따뜻한 Mint/Honey 그라데이션으로 홈 첫인상을 명확히 바꾼다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            FriendlyMoneyColors.MintDeep,
                            FriendlyMoneyColors.Mint,
                            FriendlyMoneyColors.Honey.copy(alpha = 0.82f)
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
                    color = Color.White.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.common_won, numberFormat.format(expense)),
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
                        color = Color.White.copy(alpha = 0.8f)
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
                        totalExpense = totalExpense,
                        categoryBudgets = categoryBudgets,
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
    item: CategorySum,
    index: Int,
    totalExpense: Int,
    categoryBudgets: Map<String, Int>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val category = Category.fromDisplayName(item.category)
    val categoryEmoji = rememberCategoryEmoji(item.category)
    val isCustomCategory = category == Category.ETC && item.category != Category.ETC.displayName
    val budget = categoryBudgets[item.category]
    val budgetAmount = budget ?: 0
    val hasBudget = budget != null && budgetAmount > 0
    val isOverBudget = hasBudget && item.total > budgetAmount
    val budgetRatio = if (hasBudget) item.total.toFloat() / budgetAmount else 0f
    val isWarningBudget = hasBudget && budgetRatio in 0.9f..1f
    val percentage = if (hasBudget) {
        (item.total.toFloat() / budgetAmount * 100).toInt()
    } else if (totalExpense > 0) {
        (item.total.toFloat() / totalExpense * 100).toInt()
    } else {
        0
    }
    val progress = if (hasBudget) {
        (item.total.toFloat() / budgetAmount).coerceAtMost(1f)
    } else if (totalExpense > 0) {
        item.total.toFloat() / totalExpense
    } else {
        0f
    }
    val warningColor = MaterialTheme.moneyTalkColors.calendarSunday
    val barColor = when {
        isOverBudget -> MaterialTheme.colorScheme.error
        isWarningBudget -> warningColor
        index == 0 && !hasBudget -> FriendlyMoneyColors.Mint
        isCustomCategory -> getCustomCategoryChartColor(item.category)
        else -> getCategoryChartColor(category)
    }
    val amountText = stringResource(R.string.common_won, numberFormat.format(item.total))
    val percentText = stringResource(R.string.home_category_share_percent, percentage)

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
                    color = if (isOverBudget) {
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
                                isOverBudget -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                isWarningBudget -> warningColor.copy(alpha = 0.18f)
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
                            isOverBudget -> MaterialTheme.colorScheme.error
                            isWarningBudget -> warningColor
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
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor)
                    )
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

/** AI 인사이트 카드. Gemini가 생성한 이번 달 소비 분석 요약 표시 */
@Composable
fun AiInsightCard(
    insight: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, FriendlyMoneyColors.Mint.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B3A30),
                            Color(0xFF0F4B3F),
                            Color(0xFF12392F)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_ai_insight_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FriendlyMoneyColors.Mint
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AiCoachMascot()
            }
        }
    }
}

@Composable
private fun AiCoachMascot() {
    Box(
        modifier = Modifier.size(74.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(FriendlyMoneyColors.Honey)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 7.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.8f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-7).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.8f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp)
                .width(40.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.88f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-5).dp)
                .width(54.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.92f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-5).dp)
                .width(36.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF17362F))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-7).dp, y = (-5).dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(FriendlyMoneyColors.Mint)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 7.dp, y = (-5).dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(FriendlyMoneyColors.Mint)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 2.dp)
                .width(13.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FriendlyMoneyColors.Mint.copy(alpha = 0.9f))
        )
    }
}

/** 오늘 내역 리스트에서 지출/수입을 통합 표현하기 위한 sealed interface */
sealed interface TodayItem {
    data class Expense(val expense: ExpenseEntity) : TodayItem
    data class Income(val income: IncomeEntity) : TodayItem
}

