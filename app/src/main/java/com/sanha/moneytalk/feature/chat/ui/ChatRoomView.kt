package com.sanha.moneytalk.feature.chat.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

/** 채팅방 내부 화면. 메시지 목록, 입력창, 가이드 질문 오버레이를 포함 */
@Composable
fun ChatRoomView(
    uiState: ChatUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetry: () -> Unit,
    hasApiKey: Boolean,
    onApiKeyClick: () -> Unit,
    onShowRewardAd: (Activity) -> Unit = {},
    onDismissRewardAdDialog: () -> Unit = {},
    rewardChatCount: Int = 5
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // 새 메시지가 오면 스크롤
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // 현재 세션 제목 찾기
    val currentSessionTitle = uiState.sessions
        .find { it.id == uiState.currentSessionId }
        ?.title ?: "새 대화"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 헤더 (뒤로가기 버튼 포함)
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentSessionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!hasApiKey) {
                            stringResource(R.string.chat_subtitle_no_api)
                        } else if (uiState.isLoading) {
                            stringResource(R.string.chat_subtitle_loading)
                        } else if (uiState.isRewardAdEnabled) {
                            stringResource(R.string.reward_ad_remaining, uiState.rewardChatRemaining)
                        } else if (uiState.messages.isNotEmpty()) {
                            stringResource(R.string.chat_subtitle_in_conversation)
                        } else {
                            stringResource(R.string.chat_subtitle_with_api)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!hasApiKey) {
                    TextButton(onClick = onApiKeyClick) {
                        Text(stringResource(R.string.ai_service_status_check))
                    }
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // 채팅 메시지 목록
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(message = message)
                }

                if (uiState.isLoading) {
                    item {
                        TypingIndicator()
                    }
                }

                // 재시도 버튼
                if (uiState.canRetry && !uiState.isLoading) {
                    item {
                        RetryButton(
                            onClick = onRetry
                        )
                    }
                }
            }

            // 메시지가 없을 때 가이드 질문 표시
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                GuideQuestionsOverlay(
                    questions = guideQuestions,
                    hasApiKey = hasApiKey,
                    onQuestionClick = { question ->
                        if (hasApiKey) {
                            onSendMessage(question)
                        }
                    }
                )
            }
        }

        // 입력창
        Column(
            modifier = Modifier.imePadding()
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = stringResource(R.string.chat_ai_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (uiState.isLoading) {
                                stringResource(R.string.chat_input_loading_placeholder)
                            } else {
                                stringResource(R.string.chat_input_placeholder)
                            }
                        )
                    },
                    trailingIcon = {
                        if (messageText.isNotEmpty()) {
                            IconButton(onClick = { messageText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_input))
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    enabled = hasApiKey && !uiState.isLoading
                )

                FilledIconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank() && !uiState.isLoading && hasApiKey
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send)
                    )
                }
            }
        }
    }

    // 리워드 광고 다이얼로그
    if (uiState.showRewardAdDialog) {
        RewardAdDialog(
            requiredCreditCost = uiState.pendingCreditCost,
            rewardChatCount = rewardChatCount,
            onWatchAd = {
                val activity = context as? Activity
                if (activity != null) {
                    onShowRewardAd(activity)
                }
            },
            onDismiss = onDismissRewardAdDialog
        )
    }
}
