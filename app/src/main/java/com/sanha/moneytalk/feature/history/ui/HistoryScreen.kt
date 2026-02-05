package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.sanha.moneytalk.core.model.Category
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.ExpenseItemCard
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// 카테고리별 아이콘 및 색상
data class CategoryStyle(
    val icon: String,
    val color: Color
)

private val categoryStyles = mapOf(
    "편의점" to CategoryStyle("🛒", Color(0xFF4CAF50)),
    "마트" to CategoryStyle("🛒", Color(0xFF4CAF50)),
    "고기" to CategoryStyle("🍖", Color(0xFFE91E63)),
    "일식" to CategoryStyle("🍣", Color(0xFFFF5722)),
    "중식" to CategoryStyle("🥟", Color(0xFFFF9800)),
    "한식" to CategoryStyle("🍚", Color(0xFF8BC34A)),
    "치킨" to CategoryStyle("🍗", Color(0xFFFFEB3B)),
    "피자" to CategoryStyle("🍕", Color(0xFFFF5722)),
    "패스트푸드" to CategoryStyle("🍔", Color(0xFFFFC107)),
    "분식" to CategoryStyle("🍜", Color(0xFFFF9800)),
    "배달" to CategoryStyle("🛵", Color(0xFF2196F3)),
    "카페" to CategoryStyle("☕", Color(0xFF795548)),
    "베이커리" to CategoryStyle("🥐", Color(0xFFFFCA28)),
    "아이스크림/빙수" to CategoryStyle("🍦", Color(0xFFE1BEE7)),
    "택시" to CategoryStyle("🚕", Color(0xFFFFEB3B)),
    "대중교통" to CategoryStyle("🚇", Color(0xFF2196F3)),
    "주유" to CategoryStyle("⛽", Color(0xFF607D8B)),
    "주차" to CategoryStyle("🅿️", Color(0xFF9E9E9E)),
    "온라인쇼핑" to CategoryStyle("📦", Color(0xFF3F51B5)),
    "패션" to CategoryStyle("👕", Color(0xFF9C27B0)),
    "뷰티" to CategoryStyle("💄", Color(0xFFE91E63)),
    "생활용품" to CategoryStyle("🏠", Color(0xFF00BCD4)),
    "구독" to CategoryStyle("📱", Color(0xFF673AB7)),
    "병원" to CategoryStyle("🏥", Color(0xFFF44336)),
    "약국" to CategoryStyle("💊", Color(0xFF4CAF50)),
    "운동" to CategoryStyle("💪", Color(0xFF00BCD4)),
    "영화" to CategoryStyle("🎬", Color(0xFF9C27B0)),
    "놀이공원" to CategoryStyle("🎢", Color(0xFFFF5722)),
    "게임/오락" to CategoryStyle("🎮", Color(0xFF3F51B5)),
    "여행/숙박" to CategoryStyle("✈️", Color(0xFF00BCD4)),
    "공연/전시" to CategoryStyle("🎭", Color(0xFF9C27B0)),
    "교육" to CategoryStyle("📚", Color(0xFF2196F3)),
    "도서" to CategoryStyle("📖", Color(0xFF795548)),
    "통신" to CategoryStyle("📶", Color(0xFF607D8B)),
    "공과금" to CategoryStyle("💡", Color(0xFFFFEB3B)),
    "보험" to CategoryStyle("🛡️", Color(0xFF009688)),
    "미용" to CategoryStyle("💇", Color(0xFFE91E63)),
    "식비" to CategoryStyle("🍽️", Color(0xFFFF9800)),
    "기타" to CategoryStyle("💳", Color(0xFF9E9E9E))
)

private fun getCategoryStyle(category: String): CategoryStyle {
    return categoryStyles[category] ?: categoryStyles["기타"]!!
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 메시지 표시
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Pull-to-Refresh
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 검색 모드일 때 검색 바, 아니면 일반 헤더
                if (uiState.isSearchMode) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.search(it) },
                        onClose = { viewModel.exitSearchMode() }
                    )
                } else {
                    // 헤더
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )

                    // 기간 선택 및 지출/수입 요약
                    PeriodSummaryCard(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        monthStartDay = uiState.monthStartDay,
                        totalExpense = uiState.monthlyTotal,
                        totalIncome = 0,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 뷰 토글 및 필터
                ViewToggleRow(
                    currentMode = viewMode,
                    onModeChange = { viewMode = it },
                    cardNames = uiState.cardNames,
                    selectedCardName = uiState.selectedCardName,
                    onCardNameSelected = { viewModel.filterByCardName(it) },
                    onSearchClick = { viewModel.enterSearchMode() },
                    onAddClick = { showAddDialog = true },
                    isSearchMode = uiState.isSearchMode,
                    sortOrder = uiState.sortOrder,
                    onSortOrderChange = { viewModel.setSortOrder(it) }
                )

                // 콘텐츠
                when (viewMode) {
                    ViewMode.LIST -> {
                        ExpenseListView(
                            expenses = uiState.expenses,
                            isLoading = uiState.isLoading,
                            onDelete = { viewModel.deleteExpense(it) },
                            onCategoryChange = { expense, newCategory ->
                                viewModel.updateExpenseCategory(expense.id, expense.storeName, newCategory)
                            }
                        )
                    }
                    ViewMode.CALENDAR -> {
                        BillingCycleCalendarView(
                            year = uiState.selectedYear,
                            month = uiState.selectedMonth,
                            monthStartDay = uiState.monthStartDay,
                            dailyTotals = uiState.dailyTotals
                        )
                    }
                }
            }
        }
    }

    // 수동 지출 추가 다이얼로그
    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, storeName, category, cardName ->
                viewModel.addManualExpense(amount, storeName, category, cardName)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.history_search_hint)) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                    }
                }
            }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Int, storeName: String, category: String, cardName: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("기타") }
    var cardName by remember { mutableStateOf("현금") }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    val categories = Category.entries.map { it.displayName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_add_expense_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 금액 입력
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.history_amount)) },
                    suffix = { Text("원") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 가게명 입력
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.history_store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 카테고리 선택
                Box {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.history_category)) },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryDropdown = true },
                        trailingIcon = {
                            IconButton(onClick = { showCategoryDropdown = true }) {
                                Icon(Icons.Default.List, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // 결제수단 입력
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text(stringResource(R.string.history_payment_method)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toIntOrNull() ?: 0
                    if (amount > 0 && storeName.isNotBlank()) {
                        onConfirm(amount, storeName, selectedCategory, cardName)
                    }
                },
                enabled = amountText.isNotBlank() && storeName.isNotBlank()
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

enum class ViewMode {
    LIST, CALENDAR
}

@Composable
fun PeriodSummaryCard(
    year: Int,
    month: Int,
    monthStartDay: Int,
    totalExpense: Int,
    totalIncome: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    // 기간 계산 (21일 ~ 다음달 20일 형식)
    val startDate = if (monthStartDay > 1) {
        String.format("%02d.%02d.%02d", year % 100, if (month == 1) 12 else month - 1, monthStartDay)
    } else {
        String.format("%02d.%02d.01", year % 100, month)
    }

    val endDate = if (monthStartDay > 1) {
        String.format("%02d.%02d.%02d", year % 100, month, monthStartDay - 1)
    } else {
        val lastDay = when (month) {
            2 -> if (year % 4 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        String.format("%02d.%02d.%02d", year % 100, month, lastDay)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이전 월 버튼
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_previous_month),
                    modifier = Modifier.size(28.dp)
                )
            }

            // 기간 표시
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$startDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "- $endDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 지출/수입 요약
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_expense) + " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(totalExpense)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_income) + " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(totalIncome)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // 다음 월 버튼
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun ViewToggleRow(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    cardNames: List<String>,
    selectedCardName: String?,
    onCardNameSelected: (String?) -> Unit,
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    isSearchMode: Boolean = false,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    onSortOrderChange: (SortOrder) -> Unit = {}
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 뷰 토글 버튼
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 목록 버튼
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (currentMode == ViewMode.LIST)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent
                    )
                    .clickable { onModeChange(ViewMode.LIST) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = stringResource(R.string.history_view_list),
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == ViewMode.LIST)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.history_view_list),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentMode == ViewMode.LIST)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 달력 버튼
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (currentMode == ViewMode.CALENDAR)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Transparent
                    )
                    .clickable { onModeChange(ViewMode.CALENDAR) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = stringResource(R.string.history_view_calendar),
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == ViewMode.CALENDAR)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.history_view_calendar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentMode == ViewMode.CALENDAR)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 필터 버튼
        Row {
            Box {
                FilterChip(
                    selected = selectedCardName != null,
                    onClick = { showFilterMenu = true },
                    label = {
                        Text(
                            text = selectedCardName ?: stringResource(R.string.common_filter),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_all)) },
                        onClick = {
                            onCardNameSelected(null)
                            showFilterMenu = false
                        }
                    )
                    cardNames.forEach { cardName ->
                        DropdownMenuItem(
                            text = { Text(cardName) },
                            onClick = {
                                onCardNameSelected(cardName)
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }

            if (!isSearchMode) {
                Spacer(modifier = Modifier.width(8.dp))

                // 정렬 버튼
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "정렬",
                            tint = if (sortOrder != SortOrder.DATE_DESC)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "최신순",
                                    color = if (sortOrder == SortOrder.DATE_DESC)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSortOrderChange(SortOrder.DATE_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "금액 높은순",
                                    color = if (sortOrder == SortOrder.AMOUNT_DESC)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSortOrderChange(SortOrder.AMOUNT_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "사용처별",
                                    color = if (sortOrder == SortOrder.STORE_FREQ)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSortOrderChange(SortOrder.STORE_FREQ)
                                showSortMenu = false
                            }
                        )
                    }
                }

                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                }

                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add))
                }
            }
        }
    }
}

@Composable
fun ExpenseListView(
    expenses: List<ExpenseEntity>,
    isLoading: Boolean,
    onDelete: (ExpenseEntity) -> Unit,
    onCategoryChange: (ExpenseEntity, String) -> Unit = { _, _ -> }
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\uD83D\uDCED",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_no_expense),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    // 날짜별 그룹핑
    val groupedExpenses = expenses.groupBy { expense ->
        try {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = expense.dateTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } catch (e: Exception) {
            Date()
        }
    }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedExpenses.forEach { (date, dayExpenses) ->
            val dailyTotal = dayExpenses.sumOf { it.amount }
            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekResId = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> R.string.day_sunday
                Calendar.MONDAY -> R.string.day_monday
                Calendar.TUESDAY -> R.string.day_tuesday
                Calendar.WEDNESDAY -> R.string.day_wednesday
                Calendar.THURSDAY -> R.string.day_thursday
                Calendar.FRIDAY -> R.string.day_friday
                Calendar.SATURDAY -> R.string.day_saturday
                else -> R.string.day_sunday
            }

            // 날짜 헤더
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_day_header, dayOfMonth, stringResource(dayOfWeekResId)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "-" + stringResource(R.string.common_won, numberFormat.format(dailyTotal)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }

            // 지출 항목 (공통 컴포넌트 사용)
            items(
                items = dayExpenses,
                key = { it.id }
            ) { expense ->
                ExpenseItemCard(
                    expense = expense,
                    onClick = { selectedExpense = expense }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 지출 상세 다이얼로그 (삭제 및 카테고리 변경 기능 포함)
    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = { onDelete(expense) },
            onCategoryChange = { newCategory ->
                onCategoryChange(expense, newCategory)
                selectedExpense = null
            }
        )
    }
}


// 날짜 정보를 담는 데이터 클래스
data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateString: String, // "yyyy-MM-dd" 형식
    val isCurrentPeriod: Boolean, // 현재 결제 기간에 속하는지
    val isFuture: Boolean, // 오늘 이후인지
    val isToday: Boolean
)

@Composable
fun BillingCycleCalendarView(
    year: Int,
    month: Int,
    monthStartDay: Int,
    dailyTotals: Map<String, Int> // "yyyy-MM-dd" -> amount
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val today = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 결제 기간에 해당하는 날짜 목록 생성
    val calendarDays = remember(year, month, monthStartDay) {
        generateBillingCycleDays(year, month, monthStartDay, todayYear, todayMonth, todayDay)
    }

    // 주 단위로 그룹핑
    val weeks = remember(calendarDays) {
        calendarDays.chunked(7)
    }

    // 주별 합계 계산
    val weeklyTotals = remember(weeks, dailyTotals) {
        weeks.map { week ->
            week.filter { it.isCurrentPeriod }.sumOf { day ->
                dailyTotals[day.dateString] ?: 0
            }
        }
    }

    // 무지출일 계산 (오늘까지만)
    val noSpendDays = remember(calendarDays, dailyTotals) {
        calendarDays.count { day ->
            day.isCurrentPeriod && !day.isFuture && (dailyTotals[day.dateString] ?: 0) == 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 무지출일 배너
        if (noSpendDays > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.history_no_spend_month, month),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_no_spend_total, noSpendDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 요일 헤더
        val dayLabels = listOf(
            R.string.day_sun, R.string.day_mon, R.string.day_tue,
            R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            dayLabels.forEachIndexed { index, dayResId ->
                Text(
                    text = stringResource(dayResId),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (index) {
                        0 -> Color(0xFFE91E63)
                        6 -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }

        // 달력 그리드
        LazyColumn {
            weeks.forEachIndexed { weekIndex, week ->
                val weekTotal = weeklyTotals.getOrNull(weekIndex) ?: 0

                item {
                    Column {
                        // 주간 합계 (오른쪽 정렬)
                        if (weekTotal > 0) {
                            Text(
                                text = "-${numberFormat.format(weekTotal)}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp, bottom = 2.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { calendarDay ->
                                CalendarDayCell(
                                    calendarDay = calendarDay,
                                    dayTotal = dailyTotals[calendarDay.dateString] ?: 0,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 부족한 셀 채우기
                            repeat(7 - week.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    calendarDay: CalendarDay,
    dayTotal: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.8f),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 날짜
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            calendarDay.isToday -> Color(0xFF4CAF50)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = calendarDay.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (calendarDay.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        calendarDay.isToday -> Color.White
                        calendarDay.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        !calendarDay.isCurrentPeriod -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // 일별 지출 (미래 날짜는 표시 안함)
            if (dayTotal > 0 && !calendarDay.isFuture && calendarDay.isCurrentPeriod) {
                Text(
                    text = "-${numberFormat.format(dayTotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 결제 기간에 해당하는 날짜 목록 생성
 * 예: monthStartDay가 21이면, 이전 달 21일 ~ 이번 달 20일
 */
private fun generateBillingCycleDays(
    year: Int,
    month: Int,
    monthStartDay: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int
): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    // 시작 날짜 계산
    val startCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)

        // 시작일이 1이 아니면 이전 달로 이동
        if (monthStartDay > 1) {
            add(Calendar.MONTH, -1)
        }
        set(Calendar.DAY_OF_MONTH, monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }

    // 종료 날짜 계산 (시작일 - 1 또는 월말)
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        if (monthStartDay > 1) {
            set(Calendar.DAY_OF_MONTH, (monthStartDay - 1).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
        } else {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
    }

    // 시작 주의 일요일로 이동 (캘린더 첫 행 시작)
    val displayStartCal = startCal.clone() as Calendar
    while (displayStartCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        displayStartCal.add(Calendar.DAY_OF_MONTH, -1)
    }

    // 종료 주의 토요일로 이동 (캘린더 마지막 행 끝)
    val displayEndCal = endCal.clone() as Calendar
    while (displayEndCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
        displayEndCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    // 날짜 목록 생성
    val currentCal = displayStartCal.clone() as Calendar
    while (!currentCal.after(displayEndCal)) {
        val calYear = currentCal.get(Calendar.YEAR)
        val calMonth = currentCal.get(Calendar.MONTH) + 1
        val calDay = currentCal.get(Calendar.DAY_OF_MONTH)
        val dateString = dateFormat.format(currentCal.time)

        // 현재 결제 기간에 속하는지 확인
        val isInPeriod = !currentCal.before(startCal) && !currentCal.after(endCal)

        // 미래 날짜인지 확인
        val isFuture = when {
            calYear > todayYear -> true
            calYear < todayYear -> false
            calMonth > todayMonth -> true
            calMonth < todayMonth -> false
            else -> calDay > todayDay
        }

        // 오늘인지 확인
        val isToday = calYear == todayYear && calMonth == todayMonth && calDay == todayDay

        days.add(
            CalendarDay(
                year = calYear,
                month = calMonth,
                day = calDay,
                dateString = dateString,
                isCurrentPeriod = isInPeriod,
                isFuture = isFuture,
                isToday = isToday
            )
        )

        currentCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    return days
}
