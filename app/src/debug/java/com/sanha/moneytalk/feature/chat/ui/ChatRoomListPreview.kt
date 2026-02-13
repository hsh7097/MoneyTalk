package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

// ========== 테스트 데이터 ==========

private val sampleSessions = listOf(
    ChatSession(
        id = 1,
        title = "이번 달 식비 분석",
        createdAt = System.currentTimeMillis() - 86400000,
        updatedAt = System.currentTimeMillis() - 3600000,
        messageCount = 12
    ),
    ChatSession(
        id = 2,
        title = "카테고리 정리 요청",
        createdAt = System.currentTimeMillis() - 172800000,
        updatedAt = System.currentTimeMillis() - 86400000,
        messageCount = 5
    ),
    ChatSession(
        id = 3,
        title = "지난 달 대비 지출 비교해줘",
        createdAt = System.currentTimeMillis() - 604800000,
        updatedAt = System.currentTimeMillis() - 259200000,
        messageCount = 8
    )
)

// ========== SessionItem Preview ==========

class SessionItemPreviewProvider : PreviewParameterProvider<ChatSession> {
    override val values: Sequence<ChatSession>
        get() = sampleSessions.asSequence()
}

@Preview(showBackground = true, name = "세션 아이템")
@Composable
private fun SessionItemPreview(
    @PreviewParameter(SessionItemPreviewProvider::class)
    session: ChatSession
) {
    MaterialTheme {
        SessionItem(
            session = session,
            onSelect = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true, name = "세션 아이템 목록", widthDp = 360)
@Composable
private fun SessionItemListPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                sampleSessions.forEach { session ->
                    SessionItem(
                        session = session,
                        onSelect = {},
                        onDelete = {}
                    )
                }
            }
        }
    }
}

// ========== ChatRoomListView Preview ==========

@Preview(showBackground = true, name = "채팅방 목록 - 세션 있음", widthDp = 360, heightDp = 640)
@Composable
private fun ChatRoomListViewWithSessionsPreview() {
    MaterialTheme {
        ChatRoomListView(
            sessions = sampleSessions,
            hasApiKey = true,
            onSessionSelect = {},
            onSessionDelete = {},
            onNewSession = {},
            onApiKeyClick = {}
        )
    }
}

@Preview(showBackground = true, name = "채팅방 목록 - 빈 상태", widthDp = 360, heightDp = 640)
@Composable
private fun ChatRoomListViewEmptyPreview() {
    MaterialTheme {
        ChatRoomListView(
            sessions = emptyList(),
            hasApiKey = true,
            onSessionSelect = {},
            onSessionDelete = {},
            onNewSession = {},
            onApiKeyClick = {}
        )
    }
}

@Preview(showBackground = true, name = "채팅방 목록 - API 키 없음", widthDp = 360, heightDp = 640)
@Composable
private fun ChatRoomListViewNoApiKeyPreview() {
    MaterialTheme {
        ChatRoomListView(
            sessions = sampleSessions,
            hasApiKey = false,
            onSessionSelect = {},
            onSessionDelete = {},
            onNewSession = {},
            onApiKeyClick = {}
        )
    }
}
