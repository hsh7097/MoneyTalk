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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
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

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 헤더
        Text(
            text = "가계부",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // 기간 선택 및 지출/수입 요약
        PeriodSummaryCard(
            year = uiState.selectedYear,
            month = uiState.selectedMonth,
            monthStartDay = 1, // TODO: Get from settings
            totalExpense = uiState.monthlyTotal,
            totalIncome = 0, // TODO: Add income tracking
            onPreviousMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 뷰 토글 및 필터
        ViewToggleRow(
            currentMode = viewMode,
            onModeChange = { viewMode = it },
            cardNames = uiState.cardNames,
            selectedCardName = uiState.selectedCardName,
            onCardNameSelected = { viewModel.filterByCardName(it) }
        )

        // 콘텐츠
        when (viewMode) {
            ViewMode.LIST -> {
                ExpenseListView(
                    expenses = uiState.expenses,
                    isLoading = uiState.isLoading,
                    onDelete = { viewModel.deleteExpense(it) }
                )
            }
            ViewMode.CALENDAR -> {
                CalendarView(
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    expenses = uiState.expenses,
                    dailyTotals = uiState.dailyTotals.associate {
                        it.date.takeLast(2).toIntOrNull() ?: 0 to it.total
                    }
                )
            }
        }
    }
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
                    contentDescription = "이전 달",
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
                        text = "지출 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${numberFormat.format(totalExpense)}원",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "수입 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${numberFormat.format(totalIncome)}원",
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
                    contentDescription = "다음 달",
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
    onCardNameSelected: (String?) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

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
                        contentDescription = "목록",
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == ViewMode.LIST)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "목록",
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
                        contentDescription = "달력",
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == ViewMode.CALENDAR)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "달력",
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
                            text = selectedCardName ?: "필터",
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
                        text = { Text("전체") },
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

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { /* TODO: Search */ }) {
                Icon(Icons.Default.Search, contentDescription = "검색")
            }

            IconButton(onClick = { /* TODO: Add */ }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    }
}

@Composable
fun ExpenseListView(
    expenses: List<ExpenseEntity>,
    isLoading: Boolean,
    onDelete: (ExpenseEntity) -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

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
                    text = "📭",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "지출 내역이 없어요",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    // 날짜별 그룹핑
    val groupedExpenses = expenses.groupBy { expense ->
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            val date = dateFormat.parse(expense.dateTime.take(10))
            date ?: Date()
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
            val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "일요일"
                Calendar.MONDAY -> "월요일"
                Calendar.TUESDAY -> "화요일"
                Calendar.WEDNESDAY -> "수요일"
                Calendar.THURSDAY -> "목요일"
                Calendar.FRIDAY -> "금요일"
                Calendar.SATURDAY -> "토요일"
                else -> ""
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
                        text = "${dayOfMonth}일 $dayOfWeek",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "-${numberFormat.format(dailyTotal)}원",
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

            // 지출 항목
            items(
                items = dayExpenses,
                key = { it.id }
            ) { expense ->
                BanksaladExpenseItem(
                    expense = expense,
                    onDelete = { onDelete(expense) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BanksaladExpenseItem(
    expense: ExpenseEntity,
    onDelete: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val categoryStyle = getCategoryStyle(expense.category)
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDeleteDialog = true }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // 카테고리 아이콘
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryStyle.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryStyle.icon,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                // 가게명
                Text(
                    text = expense.storeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 카테고리 | 카드 정보
                Text(
                    text = "${expense.category} | ${expense.cardName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 금액
        Text(
            text = "-${numberFormat.format(expense.amount)}원",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제 확인") },
            text = { Text("${expense.storeName} 지출 내역을 삭제할까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun CalendarView(
    year: Int,
    month: Int,
    expenses: List<ExpenseEntity>,
    dailyTotals: Map<Int, Int>
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()
    val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month - 1
    val currentDay = today.get(Calendar.DAY_OF_MONTH)

    // 주별 합계 계산
    val weeklyTotals = mutableMapOf<Int, Int>()
    var weekIndex = 0
    var dayCounter = 0
    for (day in 1..daysInMonth) {
        val dayOfWeek = (firstDayOfWeek + day - 1) % 7
        weeklyTotals[weekIndex] = (weeklyTotals[weekIndex] ?: 0) + (dailyTotals[day] ?: 0)
        if (dayOfWeek == 6 || day == daysInMonth) {
            weekIndex++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 무지출일 배너
        val noSpendDays = (1..daysInMonth).count { dailyTotals[it] == null || dailyTotals[it] == 0 }
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
                            text = "이번 달 무지출",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "총 ${noSpendDays}일 >",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 요일 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, day ->
                Text(
                    text = day,
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
            var dayNum = 1
            var currentWeek = 0

            while (dayNum <= daysInMonth) {
                val weekDays = mutableListOf<Int?>()
                val startDay = if (currentWeek == 0) firstDayOfWeek else 0

                // 첫 주 빈 칸 채우기
                if (currentWeek == 0) {
                    repeat(firstDayOfWeek) {
                        weekDays.add(null)
                    }
                }

                // 날짜 채우기
                while (weekDays.size < 7 && dayNum <= daysInMonth) {
                    weekDays.add(dayNum)
                    dayNum++
                }

                // 마지막 주 빈 칸 채우기
                while (weekDays.size < 7) {
                    weekDays.add(null)
                }

                val weekTotal = weeklyTotals[currentWeek] ?: 0

                item {
                    Column {
                        // 주간 합계
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
                            weekDays.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .aspectRatio(0.8f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    if (day != null) {
                                        val isToday = isCurrentMonth && day == currentDay
                                        val dayTotal = dailyTotals[day] ?: 0

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // 날짜
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isToday) Color(0xFF00BCD4)
                                                        else Color.Transparent
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isToday) Color.White
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            // 일별 지출
                                            if (dayTotal > 0) {
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
                            }
                        }
                    }
                }

                currentWeek++
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
