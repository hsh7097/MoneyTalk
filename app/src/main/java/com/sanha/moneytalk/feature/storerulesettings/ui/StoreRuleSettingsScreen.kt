package com.sanha.moneytalk.feature.storerulesettings.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import com.sanha.moneytalk.feature.storerulesettings.ui.coachmark.storeRuleCoachMarkSteps
import kotlinx.coroutines.delay

/**
 * 거래처 규칙 관리 화면.
 *
 * 키워드 기반 거래처 규칙(카테고리 자동 설정, 고정지출 자동 체크)을 관리한다.
 * 규칙 추가/편집/삭제를 지원한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreRuleSettingsScreen(
    onBack: () -> Unit,
    viewModel: StoreRuleSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ===== 코치마크 (거래처 규칙 온보딩) =====
    val coachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val coachMarkState = remember { CoachMarkState() }
    val allStoreRuleSteps = remember { storeRuleCoachMarkSteps() }
    val hasSeenStoreRuleOnboarding by viewModel.hasSeenScreenOnboardingFlow("store_rule")
        .collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(hasSeenStoreRuleOnboarding) {
        if (!hasSeenStoreRuleOnboarding) {
            delay(1000)
            val visibleSteps = allStoreRuleSteps.filter { it.targetKey in coachMarkRegistry.targets }
            if (visibleSteps.isNotEmpty()) {
                coachMarkState.show(visibleSteps)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.store_rule_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showAddDialog() },
                        modifier = Modifier.onboardingTarget("store_rule_add", coachMarkRegistry)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.common_add)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 설명 텍스트
            item {
                Text(
                    text = stringResource(R.string.store_rule_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (uiState.rules.isEmpty()) {
                // 빈 상태
                item {
                    Text(
                        text = stringResource(R.string.store_rule_settings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(uiState.rules, key = { it.id }) { rule ->
                    StoreRuleListItem(
                        rule = rule,
                        onEdit = { viewModel.showEditDialog(rule) },
                        onDelete = { viewModel.showDeleteConfirm(rule.id) }
                    )
                }
            }

            // 추가 버튼
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.showAddDialog() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
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
                        text = stringResource(R.string.store_rule_settings_add),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    CoachMarkOverlay(
        state = coachMarkState,
        targetRegistry = coachMarkRegistry,
        onComplete = { viewModel.markScreenOnboardingSeen("store_rule") }
    )
    } // Box

    // 추가/편집 다이얼로그
    if (uiState.showAddDialog) {
        AddEditRuleDialog(
            isEdit = uiState.editingRule != null,
            keyword = uiState.addKeyword,
            category = uiState.addCategory,
            isFixed = uiState.addIsFixed,
            error = uiState.addErrorResId?.let { stringResource(it) },
            onKeywordChange = { viewModel.updateKeyword(it) },
            onCategoryClick = { viewModel.showCategorySelect() },
            onCategoryReset = { viewModel.updateCategory(null) },
            onIsFixedChange = { viewModel.updateIsFixed(it) },
            onConfirm = { viewModel.saveRule() },
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }

    // 카테고리 선택 다이얼로그
    if (uiState.showCategorySelect) {
        CategorySelectDialog(
            currentCategory = uiState.addCategory,
            categoryType = CategoryType.EXPENSE,
            showAllOption = false,
            customCategories = uiState.categoryEntries,
            onDismiss = { viewModel.dismissCategorySelect() },
            onCategorySelected = { selected ->
                viewModel.updateCategory(selected)
            }
        )
    }

    // 삭제 확인 다이얼로그
    uiState.showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.store_rule_settings_delete_title)) },
            text = { Text(stringResource(R.string.store_rule_settings_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteRule(id) },
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
private fun StoreRuleListItem(
    rule: StoreRuleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit)
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.keyword,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rule.category != null) {
                    Text(
                        text = stringResource(R.string.store_rule_settings_category_label, rule.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rule.isFixed == true) {
                    Text(
                        text = stringResource(R.string.store_rule_settings_fixed_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                rule.isExcludedFromStats?.let { isExcluded ->
                    Text(
                        text = stringResource(
                            R.string.store_rule_settings_stats_excluded_label,
                            stringResource(
                                if (isExcluded) {
                                    R.string.store_rule_settings_stats_excluded_on
                                } else {
                                    R.string.store_rule_settings_stats_excluded_off
                                }
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
