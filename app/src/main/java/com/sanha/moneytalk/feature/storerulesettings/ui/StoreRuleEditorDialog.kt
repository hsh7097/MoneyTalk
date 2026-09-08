package com.sanha.moneytalk.feature.storerulesettings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

@Composable
internal fun AddEditRuleDialog(
    isEdit: Boolean,
    keyword: String,
    category: String?,
    isFixed: Boolean,
    error: String?,
    onKeywordChange: (String) -> Unit,
    onCategoryClick: () -> Unit,
    onCategoryReset: () -> Unit,
    onIsFixedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.store_rule_settings_edit_title
                    else R.string.store_rule_settings_add_title
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 키워드 입력
                OutlinedTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    label = { Text(stringResource(R.string.store_rule_settings_keyword_label)) },
                    placeholder = { Text(stringResource(R.string.store_rule_settings_keyword_hint)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 카테고리 선택
                Text(
                    text = stringResource(R.string.store_rule_settings_category_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = category
                            ?: stringResource(R.string.store_rule_settings_category_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (category != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onCategoryClick)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                    if (category != null) {
                        TextButton(onClick = onCategoryReset) {
                            Text(stringResource(R.string.store_rule_settings_category_reset))
                        }
                    } else {
                        TextButton(onClick = onCategoryClick) {
                            Text(stringResource(R.string.store_rule_settings_category_select))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 고정지출 토글
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIsFixedChange(!isFixed) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isFixed,
                        onCheckedChange = onIsFixedChange
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.store_rule_settings_fixed_toggle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
