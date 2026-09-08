package com.sanha.moneytalk.feature.transactionactions.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import java.text.NumberFormat

/** 모든 거래 목록이 공유하는 단건 수정 진입점. 상세 편집과 저장 책임은 분리한다. */
@Composable
fun TransactionQuickActionDialog(viewModel: TransactionQuickActionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.completedMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val target = state.target ?: return
    Dialog(
        onDismissRequest = viewModel::dismiss,
        properties = DialogProperties(
            dismissOnBackPress = !state.isSaving,
            dismissOnClickOutside = !state.isSaving,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.padding(32.dp).widthIn(max = 320.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            TransactionQuickActionContent(
                state = state,
                onDismiss = viewModel::dismiss,
                onDelete = { viewModel.confirmDelete(true) },
                onRetry = { viewModel.open(target) },
                onDetails = {
                    viewModel.dismiss()
                    when (target) {
                        is TransactionTarget.Expense -> TransactionEditActivity.open(context, expenseId = target.id)
                        is TransactionTarget.Income -> TransactionEditActivity.open(context, incomeId = target.id)
                    }
                }
            )
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmDelete(false) },
            title = { Text(stringResource(R.string.quick_transaction_delete_title)) },
            text = {
                Text(stringResource(
                    R.string.quick_transaction_delete_message,
                    state.transaction?.title.orEmpty(),
                    stringResource(R.string.common_won, NumberFormat.getNumberInstance().format(state.transaction?.amount ?: 0))
                ))
            },
            confirmButton = {
                TextButton(onClick = viewModel::delete, enabled = !state.isSaving) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmDelete(false) }, enabled = !state.isSaving) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun TransactionQuickActionContent(
    state: TransactionQuickActionUiState,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                state.transaction?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.quick_transaction_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDismiss, enabled = !state.isSaving) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
        }
        state.error?.let {
            Text(stringResource(it), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.error)
        }
        val transaction = state.transaction
        if (transaction == null) {
            if (!state.isLoading) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.quick_transaction_retry))
                }
            }
        } else {
            QuickTransactionMenuItem(
                label = stringResource(R.string.quick_transaction_details),
                icon = Icons.Outlined.Edit,
                enabled = !state.isSaving,
                onClick = onDetails
            )
            QuickTransactionMenuItem(
                label = stringResource(R.string.common_delete),
                icon = Icons.Outlined.DeleteOutline,
                enabled = !state.isSaving,
                destructive = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun QuickTransactionMenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = color.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = contentColor)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
