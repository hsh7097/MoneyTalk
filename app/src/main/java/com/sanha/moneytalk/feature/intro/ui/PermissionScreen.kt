package com.sanha.moneytalk.feature.intro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.util.toDpTextUnit

/**
 * 커스텀 권한 설명 화면.
 *
 * SMS 권한이 필요한 이유를 사용자에게 설명하고,
 * 동의/비동의를 선택할 수 있는 카드 UI를 제공한다.
 *
 * 순수 UI — 시스템 권한 요청은 IntroActivity에서 처리.
 */
@Composable
fun PermissionScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 제목
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.toDpTextUnit,
                        lineHeight = 24.toDpTextUnit
                    ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // SMS 권한 설명
                Text(
                    text = stringResource(R.string.permission_sms_label),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.toDpTextUnit,
                        lineHeight = 20.toDpTextUnit
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.permission_sms_description),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.toDpTextUnit,
                        lineHeight = 20.toDpTextUnit
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // 부가 안내
                Text(
                    text = stringResource(R.string.permission_note),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.toDpTextUnit,
                        lineHeight = 18.toDpTextUnit
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // 동의안함 / 동의함 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDisagree) {
                        Text(
                            text = stringResource(R.string.permission_disagree),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.toDpTextUnit
                        )
                    }

                    TextButton(onClick = onAgree) {
                        Text(
                            text = stringResource(R.string.permission_agree),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.toDpTextUnit
                        )
                    }
                }
            }
        }
    }
}
