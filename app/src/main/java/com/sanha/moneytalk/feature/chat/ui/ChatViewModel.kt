package com.sanha.moneytalk.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.feature.chat.data.ClaudeRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val claudeRepository: ClaudeRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val chatDao: ChatDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    init {
        loadChatHistory()
        checkApiKey()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            chatDao.getAllChats()
                .collect { chats ->
                    _uiState.update {
                        it.copy(
                            messages = chats.map { chat ->
                                ChatMessage(
                                    id = chat.id,
                                    content = chat.message,
                                    isUser = chat.isUser,
                                    timestamp = chat.timestamp
                                )
                            }
                        )
                    }
                }
        }
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(hasApiKey = claudeRepository.hasApiKey()) }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            // 사용자 메시지 저장
            val userChat = ChatEntity(
                message = message,
                isUser = true
            )
            chatDao.insert(userChat)

            _uiState.update { it.copy(isLoading = true) }

            try {
                // 재무 데이터 가져오기
                val monthStart = DateUtils.getMonthStartTimestamp()
                val monthEnd = DateUtils.getMonthEndTimestamp()

                val monthlyIncome = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)
                val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
                val recentExpenses = expenseRepository.getRecentExpenses(10)

                // 카테고리별 지출 문자열
                val categoryBreakdown = categoryExpenses.joinToString("\n") { item ->
                    val category = Category.fromDisplayName(item.category)
                    "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원"
                }.ifEmpty { "지출 내역이 없습니다." }

                // 최근 지출 문자열
                val recentExpensesStr = recentExpenses.joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원"
                }.ifEmpty { "최근 지출 내역이 없습니다." }

                // Claude에게 질문
                val result = claudeRepository.chat(
                    userMessage = message,
                    monthlyIncome = monthlyIncome,
                    totalExpense = totalExpense,
                    categoryBreakdown = categoryBreakdown,
                    recentExpenses = recentExpensesStr
                )

                result.onSuccess { response ->
                    val aiChat = ChatEntity(
                        message = response,
                        isUser = false
                    )
                    chatDao.insert(aiChat)
                }.onFailure { e ->
                    val errorChat = ChatEntity(
                        message = "죄송해요, 응답을 받는 중 오류가 발생했어요: ${e.message}",
                        isUser = false
                    )
                    chatDao.insert(errorChat)
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                val errorChat = ChatEntity(
                    message = "오류가 발생했어요: ${e.message}",
                    isUser = false
                )
                chatDao.insert(errorChat)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            claudeRepository.setApiKey(key)
            checkApiKey()
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.deleteAll()
        }
    }
}
