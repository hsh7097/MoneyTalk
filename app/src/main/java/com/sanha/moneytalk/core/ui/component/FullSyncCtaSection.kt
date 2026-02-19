package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

/**
 * 전체 동기화 해제 CTA 섹션.
 *
 * 2가지 모드:
 * - isPartial=false (기본): 데이터가 전혀 없을 때 → "이전 데이터가 없어요"
 * - isPartial=true: 일부 데이터만 있을 때 → "일부 데이터만 표시되고 있어요"
 *
 * @param onRequestFullSync 전체 동기화 해제(광고 다이얼로그) 요청 콜백
 * @param monthLabel 표시할 월 라벨 (예: "이번달", "2025년 12월")
 * @param isPartial 부분 데이터 모드 여부
 */
@Composable
fun FullSyncCtaSection(
    onRequestFullSync: () -> Unit,
    monthLabel: String,
    isPartial: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isPartial) "⚠️" else "📋",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isPartial) R.string.partial_sync_cta_title
                else R.string.full_sync_cta_title
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                if (isPartial) R.string.partial_sync_cta_subtitle
                else R.string.full_sync_cta_subtitle
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRequestFullSync) {
            Text(stringResource(R.string.full_sync_cta_button, monthLabel))
        }
    }
}
