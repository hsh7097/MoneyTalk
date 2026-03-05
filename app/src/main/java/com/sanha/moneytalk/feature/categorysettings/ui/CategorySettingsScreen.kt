package com.sanha.moneytalk.feature.categorysettings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.model.CustomCategoryInfo
import com.sanha.moneytalk.core.ui.component.EmojiPickerCompose
import com.sanha.moneytalk.feature.categorysettings.CategorySettingsViewModel

/**
 * 카테고리 설정 화면.
 *
 * 지출/수입/이체 탭별로 기본 + 커스텀 카테고리를 표시하고
 * 커스텀 카테고리의 추가/삭제를 지원한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySettingsScreen(
    onBack: () -> Unit,
    viewModel: CategorySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 (지출/수입/이체)
            CategoryTypeTabRow(
                selectedType = uiState.selectedTab,
                onTypeSelected = { viewModel.selectTab(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 기본 카테고리 헤더
                item {
                    Text(
                        text = stringResource(R.string.category_settings_default_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // 기본 카테고리 목록
                items(uiState.defaultCategories, key = { it.displayName }) { category ->
                    CategoryListItem(
                        emoji = category.emoji,
                        name = category.displayName,
                        isCustom = false,
                        onDelete = null
                    )
                }

                // 구분선
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // 커스텀 카테고리 헤더
                item {
                    Text(
                        text = stringResource(R.string.category_settings_custom_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // 커스텀 카테고리 목록
                if (uiState.customCategories.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.category_settings_custom_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(
                        uiState.customCategories,
                        key = { it.id }
                    ) { custom ->
                        CategoryListItem(
                            emoji = custom.emoji,
                            name = custom.displayName,
                            isCustom = true,
                            onDelete = { viewModel.showDeleteConfirm(custom.id) }
                        )
                    }
                }

                // 추가 버튼
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.showAddDialog() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.category_settings_add),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // 추가 다이얼로그
    if (uiState.showAddDialog) {
        AddCategoryDialog(
            emoji = uiState.addEmoji,
            name = uiState.addName,
            error = uiState.addErrorResId?.let { stringResource(it) },
            onEmojiChange = { viewModel.updateAddEmoji(it) },
            onNameChange = { viewModel.updateAddName(it) },
            onConfirm = { viewModel.addCategory() },
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }

    // 삭제 확인 다이얼로그
    uiState.showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.category_settings_delete_title)) },
            text = { Text(stringResource(R.string.category_settings_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteCategory(id) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryTypeTabRow(
    selectedType: CategoryType,
    onTypeSelected: (CategoryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryType.entries.forEach { type ->
            FilterChip(
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
                label = {
                    Text(
                        text = when (type) {
                            CategoryType.EXPENSE -> stringResource(R.string.category_type_expense)
                            CategoryType.INCOME -> stringResource(R.string.category_type_income)
                            CategoryType.TRANSFER -> stringResource(R.string.category_type_transfer)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CategoryListItem(
    emoji: String,
    name: String,
    isCustom: Boolean,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isCustom && onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    emoji: String,
    name: String,
    error: String?,
    onEmojiChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_settings_add_title)) },
        text = {
            Column {
                // 선택된 이모지 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.category_settings_emoji_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = emoji, fontSize = 28.sp)
                }

                // 이모지 그리드
                EmojiPickerCompose(
                    selectedEmoji = emoji,
                    onEmojiSelected = onEmojiChange,
                    modifier = Modifier.height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 이름 입력
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.category_settings_name_label)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
