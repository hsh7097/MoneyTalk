package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsCategorySection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStoreRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        SettingsSectionCompose(title = stringResource(R.string.settings_section_category)) {
            // 카테고리 정리 (AI 분류)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 60.dp)
                    .clickable { onIntent(SettingsIntent.ClassifyUnclassified) }
                    .alpha(if (uiState.isBackgroundClassifying || uiState.isClassifying) 0.6f else 1f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_classify_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.isBackgroundClassifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = when {
                                    !uiState.hasApiKey -> stringResource(R.string.settings_classify_no_api_key)
                                    uiState.isBackgroundClassifying -> stringResource(R.string.settings_classify_background)
                                    uiState.unclassifiedCount > 0 -> stringResource(R.string.settings_classify_unclassified, uiState.unclassifiedCount)
                                    else -> stringResource(R.string.settings_classify_done)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // 카테고리 설정
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Settings
                    override val title = stringResource(R.string.category_settings_title)
                    override val subtitle = stringResource(R.string.category_settings_subtitle)
                },
                onClick = {
                    onOpenCategories()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // 거래처 규칙
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Settings
                    override val title = stringResource(R.string.store_rule_settings_title)
                    override val subtitle = stringResource(R.string.store_rule_settings_subtitle)
                },
                onClick = {
                    onOpenStoreRules()
                }
            )
        }
    } // Box (settings_category)
}
