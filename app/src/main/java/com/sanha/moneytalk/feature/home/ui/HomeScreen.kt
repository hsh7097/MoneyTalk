package com.sanha.moneytalk.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.ExpenseItemCard
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.*

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
    val uiState by viewModel.uiState.collectAsState()

    // 선택된 지출 항목 (상세보기용)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 앱 시작 시 자동 동기화
    LaunchedEffect(autoSyncOnStart) {
        if (autoSyncOnStart) {
            viewModel.syncSmsMessages(contentResolver)
            onAutoSyncConsumed()
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
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

                // 디바이더
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
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

                // 디바이더
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // 최근 지출 내역 (카테고리 필터 적용)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.selectedCategory != null) {
                            val cat = Category.fromDisplayName(uiState.selectedCategory ?: "")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryIcon(category = cat, containerSize = 28.dp, iconSize = 20.dp)
                                Text(
                                    text = "${cat.displayName} 지출",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.home_recent_expense),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (uiState.selectedCategory != null) {
                            TextButton(onClick = { viewModel.selectCategory(null) }) {
                                Text("전체 보기")
                            }
                        }
                    }
                }

                val displayExpenses = if (uiState.selectedCategory != null) {
                    uiState.recentExpenses.filter { expense ->
                        if (uiState.selectedCategory == "기타") {
                            expense.category == "기타" || expense.category == "미분류"
                        } else {
                            expense.category == uiState.selectedCategory
                        }
                    }
                } else {
                    uiState.recentExpenses
                }

                if (displayExpenses.isEmpty()) {
                    item {
                        EmptyExpenseSection()
                    }
                } else {
                    items(displayExpenses) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            onClick = { selectedExpense = expense }
                        )
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
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.common_scroll_to_top)
                )
            }
        }
    }

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
                        val progress = uiState.classifyProgressCurrent.toFloat() / uiState.classifyProgressTotal.toFloat()
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
                        val progress = uiState.syncProgressCurrent.toFloat() / uiState.syncProgressTotal.toFloat()
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
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_income),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.common_won, numberFormat.format(income)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.home_expense),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.common_won, numberFormat.format(expense)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun CategoryExpenseSection(
    categoryExpenses: List<CategorySum>,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {}
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

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

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.home_category_expense),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (mergedExpenses.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_expense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            mergedExpenses.forEach { item ->
                val category = Category.fromDisplayName(item.category)
                val isSelected = selectedCategory == item.category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) {
                                onCategorySelected(null)
                            } else {
                                onCategorySelected(item.category)
                            }
                        }
                        .then(
                            if (isSelected) Modifier
                                .padding(horizontal = 0.dp)
                                .padding(vertical = 2.dp)
                            else Modifier.padding(vertical = 4.dp)
                        )
                        .then(
                            if (isSelected) {
                                Modifier
                                    .padding(vertical = 2.dp)
                            } else Modifier
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryIcon(category = category, containerSize = 24.dp, iconSize = 16.dp)
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(item.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
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
