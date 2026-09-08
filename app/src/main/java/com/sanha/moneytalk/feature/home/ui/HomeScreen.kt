package com.sanha.moneytalk.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.MainViewModel
import com.sanha.moneytalk.ScreenSyncUiState
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionDialog
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionViewModel
import com.sanha.moneytalk.core.ui.component.BannerAdCompose
import com.sanha.moneytalk.core.ui.component.BannerAdIds
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.ui.coachmark.homeCoachMarkSteps
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
/** 홈 탭 메인 화면. 월간 현황, 카테고리별 지출, 오늘 지출/전월 대비, 오늘 거래 내역을 표시 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    homeTabReClickEvent: kotlinx.coroutines.flow.SharedFlow<Unit>? = null
) {
    val context = LocalContext.current
    val quickActions: TransactionQuickActionViewModel = hiltViewModel(key = "HomeQuickActions")
    TransactionQuickActionDialog(quickActions)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshForDateChange() }
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
    val isCreditRewardAdEnabled by mainViewModel.adManager.isCreditRewardAdEnabledFlow
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
                isAdEnabled = isCreditRewardAdEnabled,
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
                    } else {
                        onRequestSmsPermission {
                            mainViewModel.requestMonthSync(pageYear, pageMonth)
                        }
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
                onTransactionLongClick = quickActions::open,
                onForecastTransactionClick = { TransactionEditActivity.open(context, expenseId = it) },
                coachMarkRegistry = coachMarkRegistry,
                isCurrentPage = page == pagerState.currentPage,
                coroutineScope = coroutineScope
            )
        } // HorizontalPager

        // 배너 광고 (RTDB reward_ad_enabled + 앱 진입 5회 이상)
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

    // 동기화 다이얼로그, AI 성과 요약, 월별 SMS 동기화 광고 다이얼로그는
    // Activity 레벨(MoneyTalkApp)에서 MainViewModel을 통해 표시
}
