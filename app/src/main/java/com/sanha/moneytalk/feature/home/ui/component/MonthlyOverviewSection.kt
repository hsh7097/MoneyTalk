package com.sanha.moneytalk.feature.home.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.home.ui.theme.HomeColors
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.Locale

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
        modifier = Modifier.fillMaxWidth().testTag("home-monthly-overview"),
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
                            HomeColors.MintDeep,
                            HomeColors.Mint,
                            HomeColors.Honey.copy(alpha = 0.82f)
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
                HomeExpenseAmount(stringResource(R.string.common_won, numberFormat.format(expense)))
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

/** 기존 중앙 정렬·숫자/통화 단위 크기를 유지하고, 공간이 부족할 때만 축소한다. */
@Composable
private fun HomeExpenseAmount(amountText: String) {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.displayLarge
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = (constraints.maxWidth - 1).coerceAtLeast(1)
        val measuredWidth = textMeasurer.measure(amountText, style = baseStyle, softWrap = false, maxLines = 1).size.width
        val scale = (availableWidth.toFloat() / measuredWidth.coerceAtLeast(1)).coerceAtMost(1f)
        var amountStyle = baseStyle.copy(fontSize = baseStyle.fontSize * scale)
        while (amountStyle.fontSize.value > 1f && textMeasurer.measure(
                amountText, style = amountStyle, softWrap = false, maxLines = 1
            ).size.width > availableWidth
        ) {
            amountStyle = amountStyle.copy(fontSize = (amountStyle.fontSize.value - 0.5f).coerceAtLeast(1f).sp)
        }
        Text(
            text = amountText,
            modifier = Modifier.fillMaxWidth(),
            style = amountStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            color = Color.White
        )
    }
}
