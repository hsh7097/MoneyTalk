package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportDialog(
    availableCards: List<String>,
    availableCategories: List<String>,
    currentFilter: ExportFilter,
    currentFormat: ExportFormat,
    isGoogleSignedIn: Boolean,
    onDismiss: () -> Unit,
    onFilterChange: (ExportFilter) -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onExportLocal: () -> Unit,
    onExportGoogleDrive: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    var selectedCards by remember { mutableStateOf(currentFilter.cardNames.toSet()) }
    var selectedCategories by remember { mutableStateOf(currentFilter.categories.toSet()) }
    var includeExpenses by remember { mutableStateOf(currentFilter.includeExpenses) }
    var includeIncomes by remember { mutableStateOf(currentFilter.includeIncomes) }
    var selectedFormat by remember { mutableStateOf(currentFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 형식 선택
                Text(
                    stringResource(R.string.export_format),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = {
                            selectedFormat = ExportFormat.JSON
                            onFormatChange(ExportFormat.JSON)
                        },
                        label = { Text(stringResource(R.string.export_format_json)) },
                        leadingIcon = if (selectedFormat == ExportFormat.JSON) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                    FilterChip(
                        selected = selectedFormat == ExportFormat.CSV,
                        onClick = {
                            selectedFormat = ExportFormat.CSV
                            onFormatChange(ExportFormat.CSV)
                        },
                        label = { Text(stringResource(R.string.export_format_csv)) },
                        leadingIcon = if (selectedFormat == ExportFormat.CSV) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }

                HorizontalDivider()

                // 데이터 유형
                Text(
                    stringResource(R.string.export_data_type),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = includeExpenses,
                        onClick = {
                            includeExpenses = !includeExpenses
                            onFilterChange(currentFilter.copy(includeExpenses = !includeExpenses))
                        },
                        label = { Text(stringResource(R.string.export_expense)) },
                        leadingIcon = if (includeExpenses) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                    FilterChip(
                        selected = includeIncomes,
                        onClick = {
                            includeIncomes = !includeIncomes
                            onFilterChange(currentFilter.copy(includeIncomes = !includeIncomes))
                        },
                        label = { Text(stringResource(R.string.export_income)) },
                        leadingIcon = if (includeIncomes) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }

                // 카드 필터
                if (availableCards.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.export_card_filter),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCards.forEach { card ->
                            FilterChip(
                                selected = card in selectedCards,
                                onClick = {
                                    selectedCards = if (card in selectedCards) {
                                        selectedCards - card
                                    } else {
                                        selectedCards + card
                                    }
                                    onFilterChange(currentFilter.copy(cardNames = selectedCards.toList()))
                                },
                                label = { Text(card, maxLines = 1) }
                            )
                        }
                    }
                }

                // 카테고리 필터
                if (availableCategories.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.export_category_filter),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCategories.take(10).forEach { category ->
                            FilterChip(
                                selected = category in selectedCategories,
                                onClick = {
                                    selectedCategories = if (category in selectedCategories) {
                                        selectedCategories - category
                                    } else {
                                        selectedCategories + category
                                    }
                                    onFilterChange(currentFilter.copy(categories = selectedCategories.toList()))
                                },
                                label = { Text(category, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 로컬 저장
                Button(
                    onClick = onExportLocal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_save_local))
                }

                // 구글 드라이브
                if (isGoogleSignedIn) {
                    OutlinedButton(
                        onClick = onExportGoogleDrive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_save_google_drive))
                    }
                } else {
                    OutlinedButton(
                        onClick = onSignInGoogle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_google_login))
                    }
                }

                // 취소
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

@Composable
fun GoogleDriveDialog(
    backupFiles: List<DriveBackupFile>,
    accountName: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.google_drive_title))
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh)
                    )
                }
            }
        },
        text = {
            Column {
                // 계정 정보
                accountName?.let {
                    Text(
                        text = stringResource(R.string.google_drive_account, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (backupFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.google_drive_no_backup),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backupFiles) { file ->
                            DriveBackupFileItem(
                                file = file,
                                dateFormat = dateFormat,
                                onRestore = { onRestore(file.id) },
                                onDelete = { onDelete(file.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.google_drive_logout))
            }
        }
    )
}

@Composable
fun DriveBackupFileItem(
    file: DriveBackupFile,
    dateFormat: SimpleDateFormat,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(file.createdTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Row {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = stringResource(R.string.common_restore),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ExclusionKeywordDialog(
    keywords: List<com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    // 기본 키워드와 사용자 키워드 분리
    val defaultKeywords = keywords.filter { it.source == "default" }
    val userKeywords = keywords.filter { it.source != "default" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.settings_exclusion_title))
                Text(
                    text = stringResource(R.string.settings_exclusion_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column {
                // 키워드 추가 입력
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text(stringResource(R.string.settings_exclusion_input_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                onAdd(newKeyword.trim())
                                newKeyword = ""
                            }
                        },
                        enabled = newKeyword.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.common_add))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 사용자 키워드 섹션
                    if (userKeywords.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_exclusion_user_section, userKeywords.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(userKeywords, key = { it.keyword }) { entity ->
                            ExclusionKeywordItem(
                                keyword = entity.keyword,
                                source = entity.source,
                                canDelete = true,
                                onDelete = { onRemove(entity.keyword) }
                            )
                        }
                    }

                    // 기본 키워드 섹션
                    if (defaultKeywords.isNotEmpty()) {
                        item {
                            if (userKeywords.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Text(
                                text = stringResource(R.string.settings_exclusion_default_section, defaultKeywords.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(defaultKeywords, key = { it.keyword }) { entity ->
                            ExclusionKeywordItem(
                                keyword = entity.keyword,
                                source = entity.source,
                                canDelete = false,
                                onDelete = { }
                            )
                        }
                    }

                    // 비어있을 때
                    if (keywords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_exclusion_empty),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
internal fun ExclusionKeywordItem(
    keyword: String,
    source: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = if (canDelete) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            if (source == "chat") {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_exclusion_source_chat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_delete),
                modifier = Modifier.size(18.dp),
                tint = if (canDelete) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                }
            )
        }
    }
}
