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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity

@Composable
internal fun BlockedSenderManageScreen(
    blockedSenders: List<SmsBlockedSenderEntity>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newAddress by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.sms_settings_blocked_sender_description),
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
                    value = newAddress,
                    onValueChange = { newAddress = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.sms_settings_blocked_sender_input_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (newAddress.isNotEmpty()) {
                            IconButton(onClick = { newAddress = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_input))
                            }
                        }
                    }
                )
                TextButton(
                    onClick = {
                        val value = newAddress.trim()
                        if (value.isNotBlank()) {
                            onAdd(value)
                            newAddress = ""
                        }
                    },
                    enabled = newAddress.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.common_add))
                }
            }
        }

        if (blockedSenders.isNotEmpty()) {
            items(blockedSenders, key = { it.address }) { sender ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sender.rawAddress.ifBlank { sender.address },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (sender.rawAddress.isNotBlank() && sender.rawAddress != sender.address) {
                            Text(
                                text = sender.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(sender.address) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                        text = stringResource(R.string.sms_settings_blocked_sender_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
