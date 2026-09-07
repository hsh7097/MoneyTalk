package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R

/** AI 서비스가 비활성화되었거나 연결되지 않을 때 표시 */
@Composable
fun AiServiceUnavailableDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_ai_service_unavailable_title)) },
        text = { Text(stringResource(R.string.dialog_ai_service_unavailable_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    )
}
