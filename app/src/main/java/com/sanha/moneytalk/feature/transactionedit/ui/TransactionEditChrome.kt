package com.sanha.moneytalk.feature.transactionedit.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors

@Composable
internal fun TransactionEditTopBar(
    isNew: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = TransactionEditDesignColors.textPrimary
            )
        }
        Text(
            text = stringResource(
                if (isNew) R.string.transaction_add_title else R.string.transaction_edit_title
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TransactionEditDesignColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSave) {
            Text(
                text = stringResource(R.string.transaction_edit_save),
                color = FriendlyMoneyColors.Mint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun TransactionEditBottomActions(
    isNew: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TransactionEditDesignColors.background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isNew) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TransactionEditDesignColors.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TransactionEditDesignColors.textPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(if (isNew) 1f else 2f)
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FriendlyMoneyColors.Mint,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_save),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun TransactionEditSystemBars() {
    val view = LocalView.current
    val background = TransactionEditDesignColors.background
    val isDark = FriendlyMoneyColors.isDark

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val backgroundColor = background.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.decorView.setBackgroundColor(backgroundColor)
            @Suppress("DEPRECATION")
            window.statusBarColor = backgroundColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = backgroundColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
}
