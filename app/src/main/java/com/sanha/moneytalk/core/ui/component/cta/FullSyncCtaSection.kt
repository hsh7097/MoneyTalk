package com.sanha.moneytalk.core.ui.component.cta

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors

/**
 * 기간 데이터 가져오기 CTA.
 *
 * 이전 월 데이터 가져오기/부분 동기화 안내를 동일한 CTA 스타일로 표현한다.
 */
@Composable
fun FullSyncCtaSection(
    onRequestFullSync: () -> Unit,
    monthLabel: String,
    isPartial: Boolean = false,
    isSyncing: Boolean = false,
    isAdEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isPartial) FriendlyMoneyColors.Honey else FriendlyMoneyColors.Mint
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !isSyncing, onClick = onRequestFullSync),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = FriendlyMoneyColors.elevatedCardBackground
        ),
        border = BorderStroke(1.dp, FriendlyMoneyColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isPartial) R.string.partial_sync_cta_title
                        else R.string.full_sync_cta_title
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FriendlyMoneyColors.textPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(
                        when {
                            isPartial && isAdEnabled -> R.string.partial_sync_cta_subtitle
                            isPartial && !isAdEnabled -> R.string.partial_sync_cta_subtitle_no_ad
                            !isAdEnabled -> R.string.full_sync_cta_subtitle_no_ad
                            else -> R.string.full_sync_cta_subtitle
                        },
                        monthLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = FriendlyMoneyColors.textSecondary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = accentColor
                )
            }
        }
    }
}
