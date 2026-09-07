package com.sanha.moneytalk.feature.smssettings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity

@Composable
internal fun ExcludedCardManageScreen(
    cards: List<OwnedCardEntity>,
    onAdd: (String) -> Unit,
    onExcludedChange: (String, Boolean) -> Unit
) {
    var newCardName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.sms_settings_excluded_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCardName,
                    onValueChange = { newCardName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.sms_settings_excluded_card_input_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (newCardName.isNotEmpty()) {
                            IconButton(onClick = { newCardName = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_input))
                            }
                        }
                    }
                )
                TextButton(
                    onClick = {
                        val value = newCardName.trim()
                        if (value.isNotBlank()) {
                            onAdd(value)
                            newCardName = ""
                        }
                    },
                    enabled = newCardName.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.common_add))
                }
            }
        }

        if (cards.isNotEmpty()) {
            items(cards, key = { it.cardName }) { card ->
                ExcludedCardItem(
                    card = card,
                    onExcludedChange = { excluded ->
                        onExcludedChange(card.cardName, excluded)
                    }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.sms_settings_excluded_card_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcludedCardItem(
    card: OwnedCardEntity,
    onExcludedChange: (Boolean) -> Unit
) {
    val excluded = !card.isOwned

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.cardName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    if (excluded) {
                        R.string.sms_settings_excluded_card_hidden_label
                    } else {
                        R.string.sms_settings_excluded_card_visible_label
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = excluded,
            onCheckedChange = onExcludedChange
        )
    }
}
