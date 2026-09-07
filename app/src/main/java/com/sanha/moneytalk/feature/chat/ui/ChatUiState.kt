package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.runtime.Stable

@Stable
data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Stable
data class ChatSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0
)

@Stable
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val isLoading: Boolean = false,
    /** 로딩 중인 세션 ID (다른 채팅방에서는 로딩 표시 안 함) */
    val loadingSessionId: Long? = null,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false,
    val showSessionList: Boolean = false,
    val canRetry: Boolean = false,
    /** 채팅방 내부 화면 표시 여부 (false=목록, true=채팅방 내부) */
    val isInChatRoom: Boolean = false,
    /** 리워드 광고 다이얼로그 표시 여부 */
    val showRewardAdDialog: Boolean = false,
    /** AI 크레딧 잔액 */
    val rewardChatRemaining: Int = 0,
    /** 광고 시청 후 전송할 대기 메시지 */
    val pendingMessage: String? = null,
    /** 대기 메시지 전송에 필요한 크레딧 */
    val pendingCreditCost: Int = 0,
    /** 리워드 광고 기능 활성화 여부 */
    val isRewardAdEnabled: Boolean = false
)
