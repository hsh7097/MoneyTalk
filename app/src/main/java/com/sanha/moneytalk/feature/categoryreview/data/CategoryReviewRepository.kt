package com.sanha.moneytalk.feature.categoryreview.data

import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/** 전체 기간의 미분류 지출을 관찰한다. 조회는 자동 분류나 거래 변경을 실행하지 않는다. */
@Singleton
class CategoryReviewRepository @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val dataRefreshEvent: DataRefreshEvent
) {
    fun observeExpenses(): Flow<List<ExpenseEntity>> = combine(
        expenseRepository.getExpensesByCategory(Category.UNCLASSIFIED.displayName),
        ownedCardRepository.getAllCards(),
        dataRefreshEvent.refreshEvent.onStart { emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED) }
    ) { expenses, cards, _ ->
        CategoryReviewFilter.filter(
            expenses = expenses,
            exclusionKeywords = smsExclusionRepository.getAllKeywordStrings(),
            excludedCardNames = cards.filterNot { it.isOwned }.map { it.cardName }.toSet()
        )
    }.flowOn(Dispatchers.IO)
}
