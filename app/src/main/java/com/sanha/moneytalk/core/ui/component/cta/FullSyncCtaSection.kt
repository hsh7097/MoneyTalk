package com.sanha.moneytalk.core.ui.component.cta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.theme.MoneyTalkDimens

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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, enabled = !isSyncing, onClick = onRequestFullSync),
        shape = RoundedCornerShape(MoneyTalkDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = FriendlyMoneyColors.elevatedCardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MoneyTalkDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isPartial) R.string.partial_sync_cta_title
                        else R.string.full_sync_cta_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                    color = FriendlyMoneyColors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = if (isPartial) FriendlyMoneyColors.Honey else FriendlyMoneyColors.Mint
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isPartial) FriendlyMoneyColors.Honey else FriendlyMoneyColors.Mint
                )
            }
        }
    }
}
