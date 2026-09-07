package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class ChatMessageObserverTest {
    @Test
    fun switchingRoomsCancelsOldRoomAndCannotShowItsUpdates() = runBlocking {
        withTimeout(5_000) {
            val source = MessageSource()
            val selected = MutableStateFlow<Long?>(1L)
            val results = Channel<ChatSessionMessages>(Channel.UNLIMITED)
            val job = launch(Dispatchers.Unconfined) { source.observer.observe(selected).collect { results.send(it) } }

            assertEquals(ChatSessionMessages(1L, emptyList()), results.receive())
            source.room(1L).emit(listOf(message(1L, "first")))
            assertEquals("first", results.receive().messages.single().message)

            selected.value = 2L
            assertEquals(ChatSessionMessages(2L, emptyList()), results.receive())
            assertEquals(0, source.room(1L).subscriptionCount.value)
            source.room(1L).emit(listOf(message(1L, "late old room update")))
            source.room(2L).emit(listOf(message(2L, "second")))
            assertEquals("second", results.receive().messages.single().message)
            job.cancelAndJoin()
            assertEquals(0, source.room(2L).subscriptionCount.value)
        }
    }

    @Test
    fun repeatedSessionIdsDoNotRestartDatabaseSubscription() = runBlocking {
        withTimeout(5_000) {
            val source = MessageSource()
            val selected = MutableSharedFlow<Long?>()
            val results = Channel<ChatSessionMessages>(Channel.UNLIMITED)
            val job = launch(Dispatchers.Unconfined) { source.observer.observe(selected).collect { results.send(it) } }
            selected.emit(1L)
            results.receive()
            selected.emit(1L)
            selected.emit(1L)
            source.room(1L).emit(listOf(message(1L, "updated")))

            assertEquals("updated", results.receive().messages.single().message)
            assertEquals(listOf(1L), source.requestedRooms)
            assertEquals(1, source.room(1L).subscriptionCount.value)
            job.cancelAndJoin()
        }
    }

    @Test
    fun clearingSelectionClearsMessagesAndStopsDatabaseObservation() = runBlocking {
        withTimeout(5_000) {
            val source = MessageSource()
            val selected = MutableStateFlow<Long?>(1L)
            val results = Channel<ChatSessionMessages>(Channel.UNLIMITED)
            val job = launch(Dispatchers.Unconfined) { source.observer.observe(selected).collect { results.send(it) } }
            results.receive()
            selected.value = null

            assertEquals(ChatSessionMessages(null, emptyList()), results.receive())
            assertEquals(0, source.room(1L).subscriptionCount.value)
            job.cancelAndJoin()
        }
    }

    @Test
    fun absentSelectionDoesNotQueryDatabase() = runBlocking {
        val source = MessageSource()
        assertEquals(ChatSessionMessages(null, emptyList()), source.observer.observe(MutableStateFlow(null)).first())
        assertEquals(emptyList<Long>(), source.requestedRooms)
    }

    private class MessageSource {
        private val rooms = mutableMapOf<Long, MutableSharedFlow<List<ChatEntity>>>()
        val requestedRooms = mutableListOf<Long>()
        private val dao = Proxy.newProxyInstance(ChatDao::class.java.classLoader, arrayOf(ChatDao::class.java)) { _, method, args ->
            check(method.name == "getChatsBySession") { "Unexpected database call: ${method.name}" }
            val sessionId = args[0] as Long
            requestedRooms.add(sessionId)
            room(sessionId)
        } as ChatDao
        val observer = ChatMessageObserver(dao)

        fun room(sessionId: Long) = rooms.getOrPut(sessionId) { MutableSharedFlow() }
    }

    private fun message(sessionId: Long, text: String) = ChatEntity(sessionId = sessionId, message = text, isUser = true)
}
