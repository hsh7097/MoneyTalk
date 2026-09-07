package com.sanha.moneytalk.feature.home.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors

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
