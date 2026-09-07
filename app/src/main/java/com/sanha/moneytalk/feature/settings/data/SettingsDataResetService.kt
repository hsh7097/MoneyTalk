package com.sanha.moneytalk.feature.settings.data

import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.sms.SmsFallbackScheduler
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.CategoryRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 사용자 데이터 초기화 순서와 수집 작업 차단을 함께 유지한다. 벡터 학습 데이터는 보존한다. */
class SettingsDataResetService @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val chatDao: ChatDao,
    private val budgetDao: BudgetDao,
    private val categoryRepository: CategoryRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val syncCoverageRepository: SyncCoverageRepository,
    private val dataRefreshEvent: DataRefreshEvent,
    private val classificationState: ClassificationState,
    private val smsFallbackScheduler: SmsFallbackScheduler
) {
    suspend fun reset() {
        classificationState.withRegistrationsPaused {
            withContext(Dispatchers.IO) {
                // 삭제 전에 예약된 문자도 비워 백그라운드 작업이 다시 적재하지 않게 한다.
                smsFallbackScheduler.clearPending()
                // 선택적 테이블 삭제 (벡터 데이터 보존)
                // SmsPatternEntity, StoreEmbeddingEntity는 학습 데이터이므로 유지
                expenseRepository.deleteAll()
                incomeRepository.deleteAll()
                chatDao.deleteAll()          // chat_history 삭제
                chatDao.deleteAllSessions()  // chat_sessions 삭제
                budgetDao.deleteAll()
                categoryRepository.deleteAllMappings()
                ownedCardRepository.deleteAll()

                // 설정 초기화
                settingsDataStore.saveMonthlyIncome(0)
                settingsDataStore.saveMonthStartDay(1)
                // 마지막 동기화 시간 초기화 (다음 동기화 시 전체 동기화 되도록)
                settingsDataStore.saveLastSyncTime(0L)
                settingsDataStore.saveLastRcsProviderScanTime(0L)
                // 실제 동기화 구간 기록도 함께 제거
                syncCoverageRepository.clearAll()
                // 광고 시청 기록 초기화 (월별 전체 동기화 다시 가능하도록)
                settingsDataStore.clearSyncedMonths()
            }

            // 전체 삭제 시 삭제 추적 목록도 초기화 (새 동기화에서 재수집 가능하도록)
            DeletedSmsTracker.clear()

            // gate가 열린 뒤 새 작업이 들어오기 전에 삭제 이벤트를 전달한다.
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.ALL_DATA_DELETED)
        }
    }
}
