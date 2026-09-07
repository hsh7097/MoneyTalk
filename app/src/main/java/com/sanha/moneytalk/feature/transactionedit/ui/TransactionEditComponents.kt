package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TransactionSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = TransactionEditDesignColors.card
        ),
        border = BorderStroke(1.dp, TransactionEditDesignColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TransactionEditDesignColors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (titleTrailing != null) {
                        titleTrailing()
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
internal fun TransactionDivider() {
    HorizontalDivider(color = TransactionEditDesignColors.border)
}

internal object TransactionEditDesignColors {
    val Mint = Color(0xFF43B883)
    val Coral = Color(0xFFFF6B5B)
    val Honey = Color(0xFFF4B740)
    val Sky = Color(0xFF7BB8FF)

    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val card: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val innerCard: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val segmentBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val border: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant
}
