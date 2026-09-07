package com.sanha.moneytalk.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.feature.chat.ui.coachmark.chatCoachMarkSteps
import kotlinx.coroutines.delay

/** 채팅 탭 메인 화면. 채팅방 목록과 채팅방 내부 화면을 전환하여 표시 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAiUnavailableDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = uiState.isInChatRoom) {
        viewModel.exitChatRoom()
    }

    // ===== 코치마크 (화면별 온보딩) =====
    val coachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val coachMarkState = remember { CoachMarkState() }
    val allChatSteps = remember { chatCoachMarkSteps() }
    val hasSeenChatOnboarding by viewModel.hasSeenScreenOnboardingFlow("chat")
        .collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(hasSeenChatOnboarding, uiState.isInChatRoom) {
        if (!hasSeenChatOnboarding && !uiState.isInChatRoom) {
            delay(1000)
            val visibleSteps = allChatSteps.filter { it.targetKey in coachMarkRegistry.targets }
            if (visibleSteps.isNotEmpty()) {
                coachMarkState.show(visibleSteps)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 채팅방 목록 <-> 채팅방 내부 전환 (애니메이션)
        AnimatedContent(
            targetState = uiState.isInChatRoom,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "chat_screen_transition"
        ) { isInChatRoom ->
            if (isInChatRoom) {
                ChatRoomView(
                    uiState = uiState,
                    onBack = { viewModel.exitChatRoom() },
                    onSendMessage = { viewModel.sendMessage(it) },
                    onRetry = { viewModel.retryLastMessage() },
                    hasApiKey = uiState.hasApiKey,
                    onApiKeyClick = { showAiUnavailableDialog = true },
                    onShowRewardAd = { activity -> viewModel.showRewardAd(activity) },
                    onDismissRewardAdDialog = { viewModel.onRewardAdDismissed() },
                    rewardChatCount = viewModel.getRewardChatCount()
                )
            } else {
                ChatRoomListView(
                    sessions = uiState.sessions,
                    hasApiKey = uiState.hasApiKey,
                    onSessionSelect = { viewModel.enterChatRoom(it) },
                    onSessionDelete = { showDeleteConfirm = it },
                    onNewSession = { viewModel.createNewSession() },
                    onApiKeyClick = { showAiUnavailableDialog = true },
                    coachMarkRegistry = coachMarkRegistry
                )
            }
        }

        // 코치마크 오버레이
        CoachMarkOverlay(
            state = coachMarkState,
            targetRegistry = coachMarkRegistry,
            onComplete = { viewModel.markScreenOnboardingSeen("chat") }
        )
    } // Box

    if (showAiUnavailableDialog) {
        AiServiceUnavailableDialog(
            onDismiss = { showAiUnavailableDialog = false }
        )
    }

    // 세션 삭제 확인 다이얼로그
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_delete_session_title)) },
            text = { Text(stringResource(R.string.dialog_delete_session_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(sessionId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.chat_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
