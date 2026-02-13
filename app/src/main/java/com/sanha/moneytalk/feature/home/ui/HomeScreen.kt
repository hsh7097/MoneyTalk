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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.FloatingActionButton
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
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    autoSyncOnStart: Boolean = false,
    onAutoSyncConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 선택된 지출 항목 (상세보기용)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 앱 시작 시 자동 동기화
    LaunchedEffect(autoSyncOnStart) {
        if (autoSyncOnStart) {
            viewModel.syncSmsMessages(contentResolver)
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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    // 오늘 지출 + 수입을 시간순 통합
    val todayTransactions = remember(
        uiState.todayExpenses,
        uiState.todayIncomes
    ) {
        val items = mutableListOf<TodayItem>()
        uiState.todayExpenses.forEach { items.add(TodayItem.Expense(it)) }
        uiState.todayIncomes.forEach { items.add(TodayItem.Income(it)) }
        items.sortedByDescending { item ->
            when (item) {
                is TodayItem.Expense -> item.expense.dateTime
                is TodayItem.Income -> item.income.dateTime
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    monthStartDay = uiState.monthStartDay,
                    periodLabel = uiState.periodLabel,
                    income = uiState.monthlyIncome,
                    expense = uiState.monthlyExpense,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onIncrementalSync = {
                        onRequestSmsPermission {
                            viewModel.syncSmsMessages(contentResolver, forceFullSync = false)
                        }
                    },
                    onTodaySync = {
                        onRequestSmsPermission {
                            viewModel.syncSmsMessages(contentResolver, todayOnly = true)
                        }
                    },
                    onFullSync = {
                        onRequestSmsPermission {
                            viewModel.syncSmsMessages(contentResolver, forceFullSync = true)
                        }
                    },
                    isSyncing = uiState.isSyncing
                )
            }

            // 카테고리별 지출
            item {
                CategoryExpenseSection(
                    categoryExpenses = uiState.categoryExpenses,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { category ->
                        viewModel.selectCategory(category)
                    }
                )
            }

            // 오늘의 지출 + 전월 대비
            item {
                TodayAndComparisonSection(
                    todayExpense = uiState.todayExpense,
                    todayExpenseCount = uiState.todayExpenseCount,
                    monthlyExpense = uiState.monthlyExpense,
                    lastMonthExpense = uiState.lastMonthExpense,
                    comparisonPeriodLabel = uiState.comparisonPeriodLabel,
                    aiInsight = uiState.aiInsight
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
                            onClick = { selectedExpense = item.expense }
                        )
                        is TodayItem.Income -> TransactionCardCompose(
                            info = IncomeTransactionCardInfo(item.income),
                            onClick = { }
                        )
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
    }

    // 지출 상세 다이얼로그 (공통 컴포넌트 사용)
    selectedExpense?.let { expense ->
        Log.e("sanha", "HomeScreen[selectedExpense] : ${expense.storeName}, ${expense.amount}원")
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = {
                viewModel.deleteExpense(expense)
                selectedExpense = null
            },
            onCategoryChange = { newCategory ->
                viewModel.updateExpenseCategory(
                    expenseId = expense.id,
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
            title = { Text("지출 내역 읽는 중") },
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
}

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
    onTodaySync: () -> Unit,
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
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.home_next_month)
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
                            text = { Text(stringResource(R.string.home_sync_today)) },
                            onClick = {
                                showSyncMenu = false
                                onTodaySync()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Notifications, contentDescription = null)
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

@Composable
fun CategoryExpenseSection(
    categoryExpenses: List<CategorySum>,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {}
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    var showAll by remember { mutableStateOf(false) }

    // 기타 + 미분류를 하나로 합치고, 금액 내림차순 정렬
    val mergedExpenses = remember(categoryExpenses) {
        val etcTotal = categoryExpenses
            .filter { it.category == "기타" || it.category == "미분류" }
            .sumOf { it.total }
        val others = categoryExpenses
            .filter { it.category != "기타" && it.category != "미분류" }
        val result = others.toMutableList()
        if (etcTotal > 0) {
            result.add(CategorySum("기타", etcTotal))
        }
        result.sortedByDescending { it.total }
    }

    val totalExpense = remember(mergedExpenses) {
        mergedExpenses.sumOf { it.total }
    }

    // TOP 3 또는 전체 표시
    val displayList = remember(mergedExpenses, showAll) {
        if (showAll) mergedExpenses else mergedExpenses.take(3)
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
                text = stringResource(R.string.home_expense_top3),
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
            val barColor = MaterialTheme.moneyTalkColors.income
            val rankAlphas = listOf(1.0f, 0.65f, 0.4f)

            displayList.forEachIndexed { index, item ->
                val category = Category.fromDisplayName(item.category)
                val percentage = if (totalExpense > 0) {
                    (item.total.toFloat() / totalExpense * 100).toInt()
                } else 0
                val progress = if (totalExpense > 0) {
                    item.total.toFloat() / totalExpense
                } else 0f
                val isSelected = selectedCategory == item.category
                val alpha = rankAlphas.getOrElse(index) { 0.3f }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) onCategorySelected(null)
                            else onCategorySelected(item.category)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // 카테고리명 + 퍼센트
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryIcon(
                                category = category,
                                containerSize = 28.dp,
                                fontSize = 18.sp
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${percentage}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 프로그레스 바 (순위별 알파)
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
                                .background(barColor.copy(alpha = alpha))
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 금액 (우측 정렬)
                    Text(
                        text = "₩${numberFormat.format(item.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}


@Composable
fun TodayAndComparisonSection(
    todayExpense: Int,
    todayExpenseCount: Int,
    monthlyExpense: Int,
    lastMonthExpense: Int,
    comparisonPeriodLabel: String,
    aiInsight: String
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 오늘의 지출 + 전월 대비 (가로 2분할, 동일 스펙)
        val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        val cardShape = RoundedCornerShape(12.dp)
        val labelStyle = MaterialTheme.typography.bodySmall
        val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        val valueStyle = MaterialTheme.typography.titleMedium
        val subStyle = MaterialTheme.typography.bodySmall
        val subColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

        // 전월 대비 계산
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 오늘의 지출 카드
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = cardShape
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = stringResource(R.string.home_today_expense), style = labelStyle, color = labelColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "₩${numberFormat.format(todayExpense)}", style = valueStyle, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (todayExpenseCount > 0) stringResource(R.string.home_today_count, todayExpenseCount) else " ",
                        style = subStyle,
                        color = subColor
                    )
                }
            }

            // 전월 대비 카드
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = cardShape
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = stringResource(R.string.home_vs_last_month), style = labelStyle, color = labelColor)
                        if (comparisonPeriodLabel.isNotBlank()) {
                            Text(
                                text = "($comparisonPeriodLabel)",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = comparisonValue, style = valueStyle, fontWeight = FontWeight.Bold, color = comparisonValueColor)
                    Text(text = comparisonSub, style = subStyle, color = subColor)
                }
            }
        }

        // AI 인사이트 (API 키 있고 데이터 있을 때만 표시)
        if (aiInsight.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = aiInsight,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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

/** 오늘 내역 리스트에서 지출/수입을 통합 표현하기 위한 sealed interface */
sealed interface TodayItem {
    data class Expense(val expense: ExpenseEntity) : TodayItem
    data class Income(val income: IncomeEntity) : TodayItem
}
