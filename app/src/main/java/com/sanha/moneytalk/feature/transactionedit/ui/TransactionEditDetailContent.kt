package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType

/**
 * 거래 상세/편집 공통 콘텐츠.
 *
 * 상세 화면처럼 읽히되, 거래처/금액/유형/카테고리/날짜/메모를 즉시 수정할 수 있도록
 * 시각 구조만 카드형으로 재배치한다.
 */
@Composable
internal fun TransactionEditDetailContent(
    uiState: TransactionEditUiState,
    hasSeenKeywordGuide: Boolean,
    coachMarkRegistry: CoachMarkTargetRegistry,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStoreNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryClick: () -> Unit,
    onApplyCategoryToAllChange: (Boolean) -> Unit,
    onDateTimeClick: () -> Unit,
    onMemoChange: (String) -> Unit,
    onFixedToggle: (Boolean) -> Unit,
    onStatsExcludeToggle: (Boolean) -> Unit,
    onApplyStatsExcludeToAllChange: (Boolean) -> Unit,
    onApplyFixedToAllChange: (Boolean) -> Unit,
    onRuleKeywordChange: (String) -> Unit,
    onKeywordGuideDismiss: () -> Unit
) {
    TransactionEditSystemBars()
    val hasCategorySameStoreRule = !uiState.isNew && uiState.applyCategoryToAll
    val hasAutomationSameStoreRule = !uiState.isNew &&
        (uiState.applyFixedToAll || uiState.applyStatsExcludeToAll)
    val shouldShowSameStoreRule = hasCategorySameStoreRule || hasAutomationSameStoreRule

    val onCategorySameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyCategoryToAllChange(checked)
    }
    val onFixedSameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyFixedToAllChange(checked)
    }
    val onStatsSameStoreChange: (Boolean) -> Unit = { checked ->
        onApplyStatsExcludeToAllChange(checked)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TransactionEditDesignColors.background)
    ) {
        TransactionEditTopBar(
            isNew = uiState.isNew,
            onClose = onClose,
            onSave = onSave
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransactionHeroCard(
                uiState = uiState,
                onStoreNameChange = onStoreNameChange,
                onAmountChange = onAmountChange,
                onTypeChange = onTypeChange
            )

            TransactionBasicInfoCard(
                uiState = uiState,
                coachMarkRegistry = coachMarkRegistry,
                onCategoryClick = onCategoryClick,
                onApplyCategoryToAllChange = onCategorySameStoreChange,
                onDateTimeClick = onDateTimeClick,
                onMemoChange = onMemoChange
            )

            TransactionAutomationCard(
                uiState = uiState,
                coachMarkRegistry = coachMarkRegistry,
                onFixedToggle = onFixedToggle,
                onStatsExcludeToggle = onStatsExcludeToggle,
                onApplyFixedToAllChange = onFixedSameStoreChange,
                onApplyStatsExcludeToAllChange = onStatsSameStoreChange
            )

            AnimatedVisibility(
                visible = shouldShowSameStoreRule,
                enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 320),
                        expandFrom = Alignment.Top
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 260),
                        shrinkTowards = Alignment.Top
                    )
            ) {
                TransactionSameStoreRuleCard(
                    uiState = uiState,
                    hasSeenKeywordGuide = hasSeenKeywordGuide,
                    onRuleKeywordChange = onRuleKeywordChange,
                    onKeywordGuideDismiss = onKeywordGuideDismiss
                )
            }

            if (!uiState.isNew && uiState.originalSms.isNotBlank()) {
                TransactionOriginalSmsCard(originalSms = uiState.originalSms)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        TransactionEditBottomActions(
            isNew = uiState.isNew,
            onSave = onSave,
            onDelete = onDelete
        )
    }
}
