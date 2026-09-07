package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

@Composable
internal fun TransactionOriginalSmsCard(originalSms: String) {
    TransactionSectionCard(
        title = stringResource(R.string.transaction_edit_original_sms)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TransactionEditDesignColors.innerCard)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                tint = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = originalSms,
                style = MaterialTheme.typography.bodySmall,
                color = TransactionEditDesignColors.textSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
