package com.sanha.moneytalk.core.appfunctions

import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.notification.NotificationTestDependencies
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleSyncService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MoneyTalkAppFunctionExecutionTest {
    private lateinit var database: AppDatabase
    private lateinit var reader: MoneyTalkChatAppFunctionReader
    private lateinit var actions: MoneyTalkChatAppFunctionActionExecutor
    private lateinit var refreshEvent: DataRefreshEvent

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val expenses = ExpenseRepository(database.expenseDao())
        val incomes = IncomeRepository(database.incomeDao())
        val settings = SettingsDataStore(context)
        val exclusions = SmsExclusionRepository(database.smsExclusionKeywordDao())
        val cards = OwnedCardRepository(database.ownedCardDao())
        val rules = StoreRuleRepository(database.storeRuleDao())
        val categories = CustomCategoryRepository(database.customCategoryDao())
        val classifier = Proxy.newProxyInstance(
            CategoryClassifierService::class.java.classLoader,
            arrayOf(CategoryClassifierService::class.java)
        ) { _, method, _ ->
            throw AssertionError("This App Function operation must not classify using ${method.name}")
        } as CategoryClassifierService
        refreshEvent = DataRefreshEvent()
        reader = MoneyTalkChatAppFunctionReader(
            context, expenses, incomes, settings, exclusions, cards, rules, categories,
            database.budgetDao(), MoneyTalkAppFunctionAnalyticsCalculator()
        )
        actions = MoneyTalkChatAppFunctionActionExecutor(
            context, expenses, incomes, settings, exclusions, cards, rules,
            StoreRuleSyncService(rules, expenses, classifier), categories,
            CategoryReferenceProvider(database.categoryMappingDao()), refreshEvent, database.budgetDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun expenseWriteIsReadableAndRetainsRefreshAndTypedAnalysis() = runBlocking(Dispatchers.IO) {
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { refreshEvent.refreshEvent.first() }
        val created = actions.addExpense("분리 테스트", 12_000, "2026-09-08", null, "식비", "기존 메모")
        assertTrue(created.success)
        assertTrue(created.resourceId > 0L)
        assertEquals(DataRefreshEvent.RefreshType.TRANSACTION_ADDED, withTimeout(2_000) { refresh.await() })

        val updated = actions.updateExpenseMemo(created.resourceId, "수정 메모")
        assertEquals(MoneyTalkOperationResult(true, "success", 1, created.resourceId), updated)
        val transaction = reader.getExpenses("2026-09-08", "2026-09-08", null, null).expenses.single()
        assertEquals(created.resourceId, transaction.id)
        assertEquals("수동입력", transaction.cardName)
        assertEquals("수정 메모", transaction.memo)
        val analysis = reader.analyzeExpenses("2026-09-08", "2026-09-08", null, null, null, null, null)
        assertEquals(12_000, analysis.results.single().metrics.first().value)
        assertEquals(1, analysis.filteredCount)
    }

    @Test
    fun incomeWriteRetainsDefaultsRecurringDayClampAndMemo() = runBlocking(Dispatchers.IO) {
        val created = actions.addIncome("회사", null, 100_000, "2026-09-08", null, null, "급여", true, 40)
        assertTrue(created.success)
        val stored = requireNotNull(database.incomeDao().getIncomeById(created.resourceId))
        assertEquals("입금", stored.type)
        assertEquals("회사", stored.description)
        assertEquals(31, stored.recurringDay)
        assertEquals("급여", stored.memo)

        assertTrue(actions.updateIncomeMemo(created.resourceId, "입금 확인").success)
        assertEquals("입금 확인", reader.getIncomes("2026-09-08", "2026-09-08", null).incomes.single().memo)
        assertEquals(100_000, reader.getTotalIncome("2026-09-08", "2026-09-08").totalAmount)
    }

    @Test
    fun storeRuleActionStillAppliesThroughDomainService() = runBlocking(Dispatchers.IO) {
        val created = actions.addExpense("테스트상점", 5_000, "2026-09-08", null, "미분류", null)
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { refreshEvent.refreshEvent.first() }
        val result = actions.upsertStoreRule("테스트상점", "식비", true, true)

        assertTrue(result.success)
        assertEquals(DataRefreshEvent.RefreshType.CATEGORY_UPDATED, withTimeout(2_000) { refresh.await() })
        val stored = requireNotNull(database.expenseDao().getExpenseById(created.resourceId))
        assertEquals("식비", stored.category)
        assertTrue(stored.isFixed)
        assertTrue(stored.isExcludedFromStats)
        assertEquals(1, reader.getStoreRules().rules.size)
    }

    @Test
    fun invalidWriteAndMissingIdLeaveDatabaseUnchanged() = runBlocking(Dispatchers.IO) {
        val invalid = actions.addExpense("상점", 0, null, null, null, null)
        val missing = actions.updateExpenseMemo(Long.MAX_VALUE, "없는 거래")
        assertFalse(invalid.success)
        assertEquals("missing_required_parameter", invalid.resultCode)
        assertEquals("not_found", missing.resultCode)
        assertEquals(0, database.expenseDao().getExpenseCount())

        val invalidDate = runCatching {
            actions.addExpense("상점", 1_000, "2026-02-30", null, null, null)
        }.exceptionOrNull()
        assertTrue(invalidDate is IllegalArgumentException)
        assertEquals(0, database.expenseDao().getExpenseCount())
    }

    /** 앱 Hilt 연결을 검증하는 합성 거래는 개인 기기에서 실행하지 않는다. */
    @Test
    fun applicationEntryPointSupportsWriteReadAndAnalysisWithExactCleanup() = runBlocking(Dispatchers.IO) {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "App Function graph fixtures must run on a disposable emulator"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entryPoint = EntryPointAccessors.fromApplication(context, MoneyTalkAppFunctionEntryPoint::class.java)
        val appDatabase = EntryPointAccessors.fromApplication(context, NotificationTestDependencies::class.java).database()
        val fixtureStore = "AppFunction-QA-${UUID.randomUUID()}"
        var fixtureId: Long? = null
        try {
            val result = entryPoint.chatAppFunctionActionExecutor()
                .addExpense(fixtureStore, 4_321, "2026-09-08", "QA카드", "식비", "합성 거래")
            fixtureId = result.resourceId.takeIf { it > 0L }
            assertTrue(result.success)
            val graphReader = entryPoint.chatAppFunctionReader()
            val records = graphReader.getExpensesByStore(fixtureStore, "2026-09-08", "2026-09-08", null)
            assertEquals(result.resourceId, records.expenses.single().id)
            val analysis = graphReader.analyzeExpenses(
                "2026-09-08", "2026-09-08",
                listOf(MoneyTalkAnalyticsFilter("storeName", "==", fixtureStore, false)),
                null, null, null, null
            )
            assertEquals(1, analysis.filteredCount)
            assertEquals(4_321, analysis.results.single().metrics.first().value)
        } finally {
            fixtureId?.let { appDatabase.expenseDao().deleteById(it) }
        }
    }
}
