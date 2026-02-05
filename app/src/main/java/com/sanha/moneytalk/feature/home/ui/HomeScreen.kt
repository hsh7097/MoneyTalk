package com.sanha.moneytalk.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    autoSyncOnStart: Boolean = false
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
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 월간 현황 카드
        item {
            MonthlyOverviewCard(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                monthStartDay = uiState.monthStartDay,
                periodLabel = uiState.periodLabel,
                income = uiState.monthlyIncome,
                expense = uiState.monthlyExpense,
                remaining = uiState.remainingBudget,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onIncrementalSync = {
                    onRequestSmsPermission {
                        viewModel.syncSmsMessages(contentResolver, forceFullSync = false)
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
            CategoryExpenseCard(
                categoryExpenses = uiState.categoryExpenses
            )
        }

        // 최근 지출 내역
        item {
            Text(
                text = "최근 지출",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.recentExpenses.isEmpty()) {
            item {
                EmptyExpenseCard()
            }
        } else {
            items(uiState.recentExpenses) { expense ->
                ExpenseItem(
                    expense = expense,
                    onClick = { selectedExpense = expense }
                )
            }
        }
    }

    // 지출 상세 다이얼로그
    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null }
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
}

@Composable
fun MonthlyOverviewCard(
    year: Int,
    month: Int,
    monthStartDay: Int,
    periodLabel: String,
    income: Int,
    expense: Int,
    remaining: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncrementalSync: () -> Unit,
    onFullSync: () -> Unit,
    isSyncing: Boolean
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val progress = if (income > 0) expense.toFloat() / income.toFloat() else 0f
    var showSyncMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
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
                        contentDescription = "이전 달"
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row {
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "다음 달"
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
                                    contentDescription = "동기화"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSyncMenu,
                            onDismissRequest = { showSyncMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("새 문자만 동기화") },
                                onClick = {
                                    showSyncMenu = false
                                    onIncrementalSync()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("전체 문자 다시 읽기") },
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
                        text = "수입",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${numberFormat.format(income)}원",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "지출",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${numberFormat.format(expense)}원",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "남은 예산: ${numberFormat.format(remaining)}원",
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CategoryExpenseCard(
    categoryExpenses: List<CategorySum>
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "카테고리별 지출",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (categoryExpenses.isEmpty()) {
                Text(
                    text = "아직 지출 내역이 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                categoryExpenses.forEach { item ->
                    val category = Category.fromDisplayName(item.category)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${category.emoji} ${category.displayName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${numberFormat.format(item.total)}원",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(
    expense: ExpenseEntity,
    onClick: () -> Unit = {}
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val category = Category.fromDisplayName(expense.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = category.emoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Column {
                    Text(
                        text = expense.storeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = DateUtils.formatDisplayDate(expense.dateTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = "-${numberFormat.format(expense.amount)}원",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ExpenseDetailDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val category = Category.fromDisplayName(expense.category)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text(
                text = category.emoji,
                style = MaterialTheme.typography.displaySmall
            )
        },
        title = {
            Text(
                text = expense.storeName,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 금액
                DetailRow(label = "금액", value = "-${numberFormat.format(expense.amount)}원")

                // 카테고리
                DetailRow(label = "카테고리", value = "${category.emoji} ${category.displayName}")

                // 카드
                DetailRow(label = "카드", value = expense.cardName)

                // 결제 시간
                DetailRow(label = "결제 시간", value = DateUtils.formatDisplayDateTime(expense.dateTime))

                // 메모
                expense.memo?.let { memo ->
                    if (memo.isNotBlank()) {
                        DetailRow(label = "메모", value = memo)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 원본 문자
                Text(
                    text = "원본 문자",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = expense.originalSms,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyExpenseCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📭",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "아직 지출 내역이 없어요",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "문자를 동기화해서 지출을 추가해보세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
