package com.sanha.moneytalk.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.firebase.ForceUpdateState

/** 강제 업데이트 다이얼로그. 닫기 불가 — 업데이트 또는 종료만 가능 */
@Composable
fun ForceUpdateDialog(
    state: ForceUpdateState.Required,
    onUpdate: () -> Unit,
    onExit: () -> Unit
) {
    val message = if (state.message.isNotBlank()) {
        state.message
    } else {
        stringResource(
            R.string.force_update_message,
            state.requiredVersion,
            state.currentVersion
        )
    }

    AlertDialog(
        onDismissRequest = { /* 닫기 차단 — 강제 업데이트 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                text = stringResource(R.string.force_update_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.force_update_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.force_update_exit))
            }
        }
    )
}
