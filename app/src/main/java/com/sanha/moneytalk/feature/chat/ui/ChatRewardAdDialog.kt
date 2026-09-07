package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R

/** 리워드 광고 시청 안내 다이얼로그 */
@Composable
fun RewardAdDialog(
    requiredCreditCost: Int,
    rewardChatCount: Int,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reward_ad_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.reward_ad_dialog_message,
                    requiredCreditCost,
                    rewardChatCount
                )
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = onWatchAd) {
                Text(stringResource(R.string.reward_ad_watch_button, rewardChatCount))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reward_ad_later))
            }
        }
    )
}
