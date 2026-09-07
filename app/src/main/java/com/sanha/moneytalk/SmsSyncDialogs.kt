package com.sanha.moneytalk

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.sms.SmsPipeline
import com.sanha.moneytalk.core.util.toDpTextUnit

/** 동기화 진행 표시. 상태와 사용자 이벤트만 받아 렌더링한다. */
@Composable
internal fun SmsSyncProgressDialog(dialogUiState: MainDialogUiState, onDismiss: () -> Unit) {
    // SMS 동기화 진행 다이얼로그 (Stepper UI + 스킵 가능)
    if (dialogUiState.showSyncDialog) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.home_sync_dialog_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncStepIndicator(
                        currentStep = dialogUiState.syncStepIndex,
                        totalSteps = SmsPipeline.TOTAL_STEPS
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (dialogUiState.syncProgressTotal > 0) {
                        val progress =
                            dialogUiState.syncProgressCurrent.toFloat() / dialogUiState.syncProgressTotal.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${dialogUiState.syncProgressCurrent} / ${dialogUiState.syncProgressTotal}건",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = dialogUiState.syncProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text(stringResource(R.string.sync_dialog_dismiss))
                }
            }
        )
    }

}

@Composable
internal fun SmsEngineSummaryDialog(dialogUiState: MainDialogUiState, onDismiss: () -> Unit) {
    // AI 성과 요약 카드 (초기 동기화 완료 후)
    if (dialogUiState.showEngineSummary) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(
                    text = stringResource(R.string.engine_summary_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dialogUiState.engineSummaryTotalSms > 0) {
                        Text(
                            text = stringResource(R.string.engine_summary_sms_analyzed, dialogUiState.engineSummaryTotalSms),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (dialogUiState.engineSummaryPatterns > 0) {
                        Text(
                            text = stringResource(R.string.engine_summary_patterns_learned, dialogUiState.engineSummaryPatterns),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    val parts = mutableListOf<String>()
                    if (dialogUiState.engineSummaryExpenses > 0) {
                        parts.add(stringResource(R.string.engine_summary_expense_count, dialogUiState.engineSummaryExpenses))
                    }
                    if (dialogUiState.engineSummaryIncomes > 0) {
                        parts.add(stringResource(R.string.engine_summary_income_count, dialogUiState.engineSummaryIncomes))
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.engine_summary_registered, parts.joinToString(" · ")),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        )
    }

}

/** 동기화 진행 단계 인디케이터 (5단계 Stepper) */
@Composable
private fun SyncStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val stepLabels = stringArrayResource(R.array.sync_step_labels)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isCurrent = i == currentStep
            val animatedDotSize by animateDpAsState(
                targetValue = if (isCurrent) 12.dp else 8.dp,
                label = "dotSize"
            )
            val dotColor = when {
                isCompleted || isCurrent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(animatedDotSize)
                        .background(color = dotColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stepLabels.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted || isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    fontSize = 9.toDpTextUnit
                )
            }

            // 단계 사이 연결선
            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            color = if (i < currentStep) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
    }
}
