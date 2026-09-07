package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

data class ChatSessionMessages(
    val sessionId: Long?,
    val messages: List<ChatEntity>
)

/** 선택된 채팅방만 구독하고, 전환/삭제하면 이전 방의 구독을 취소한다. */
class ChatMessageObserver @Inject constructor(
    private val chatDao: ChatDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(sessionIds: Flow<Long?>): Flow<ChatSessionMessages> {
        return sessionIds.distinctUntilChanged().flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(ChatSessionMessages(null, emptyList()))
            } else {
                chatDao.getChatsBySession(sessionId)
                    .map { ChatSessionMessages(sessionId, it) }
                    .onStart { emit(ChatSessionMessages(sessionId, emptyList())) }
            }
        }
    }
}
