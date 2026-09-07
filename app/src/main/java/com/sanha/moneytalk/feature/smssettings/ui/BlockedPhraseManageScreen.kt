package com.sanha.moneytalk.feature.smssettings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity

@Composable
internal fun BlockedPhraseManageScreen(
    keywords: List<SmsExclusionKeywordEntity>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }
    val defaultKeywords = keywords.filter { it.source == "default" }
    val userKeywords = keywords.filter { it.source != "default" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_exclusion_description),
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
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.settings_exclusion_input_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (newKeyword.isNotEmpty()) {
                            IconButton(onClick = { newKeyword = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_input))
                            }
                        }
                    }
                )
                TextButton(
                    onClick = {
                        val value = newKeyword.trim()
                        if (value.isNotBlank()) {
                            onAdd(value)
                            newKeyword = ""
                        }
                    },
                    enabled = newKeyword.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.common_add))
                }
            }
        }

        if (userKeywords.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_exclusion_user_section, userKeywords.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(userKeywords, key = { it.keyword }) { keyword ->
                BlockedPhraseItem(
                    keyword = keyword.keyword,
                    source = keyword.source,
                    canDelete = true,
                    onDelete = { onRemove(keyword.keyword) }
                )
            }
        }

        if (defaultKeywords.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_exclusion_default_section, defaultKeywords.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(defaultKeywords, key = { it.keyword }) { keyword ->
                BlockedPhraseItem(
                    keyword = keyword.keyword,
                    source = keyword.source,
                    canDelete = false,
                    onDelete = {}
                )
            }
        }

        if (keywords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_exclusion_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedPhraseItem(
    keyword: String,
    source: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyLarge
            )
            if (source == "chat") {
                Text(
                    text = stringResource(R.string.settings_exclusion_source_chat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = canDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}
