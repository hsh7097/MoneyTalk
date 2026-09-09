package com.sanha.moneytalk.core.sms

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.dao.ExpenseIngestionResult
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionSourceDeletionInstrumentedTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var database: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var service: TransactionQuickActionService
    private val preferenceName = "source_deletion_test_${UUID.randomUUID()}"
    private val baseTime = 1_783_332_000_000L

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        DeletedSmsTracker.init(trackerContext())
        DeletedSmsTracker.clear()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        expenses = ExpenseRepository(database.expenseDao())
        val categories = CustomCategoryRepository(database.customCategoryDao())
        service = TransactionQuickActionService(
            database, expenses, IncomeRepository(database.incomeDao()),
            CategoryProvider(categories), categories, DataRefreshEvent()
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::context.isInitialized) {
            DeletedSmsTracker.clear()
            DeletedSmsTracker.init(context)
            context.deleteSharedPreferences(preferenceName)
        }
    }

    @Test
    fun replacingAppWithSmsThenDeletingBlocksBothSourcesAfterReload() = runBlocking(Dispatchers.IO) {
        assertBothSourcesStayDeleted(appFirst = true)
    }

    @Test
    fun skippingAppAfterSmsThenDeletingBlocksBothSourcesAfterReload() = runBlocking(Dispatchers.IO) {
        assertBothSourcesStayDeleted(appFirst = false)
    }

    @Test
    fun sameAmountDifferentCardIsNeverLinked() = runBlocking(Dispatchers.IO) {
        val sms = expense("sms")
        val other = expense("other-app", app = true).copy(
            cardName = "삼성", originalSms = "삼성카드 5678 승인 테스트상점 12,000원"
        )
        val smsId = (expenses.insertIngested(sms) as ExpenseIngestionResult.Inserted).expenseId
        val otherId = (expenses.insertIngested(other) as ExpenseIngestionResult.Inserted).expenseId
        assertTrue(service.delete(TransactionTarget.Expense(smsId)))
        assertFalse(DeletedSmsTracker.isDeleted(other.smsId))
        assertEquals(otherId, expenses.getAllExpensesOnce().single().id)
    }

    @Test
    fun arbitrarySameRowIdentityChangeDoesNotCreateSourceLink() = runBlocking(Dispatchers.IO) {
        val original = expense("original")
        val id = expenses.insert(original)
        expenses.insertIngested(original.copy(id = id, smsId = "arbitrary"), reconcileExisting = true)
        assertTrue(service.delete(TransactionTarget.Expense(id)))
        assertTrue(DeletedSmsTracker.isDeleted("arbitrary"))
        assertFalse(DeletedSmsTracker.isDeleted("original"))
    }

    @Test
    fun semanticLinkSurvivesWriteRollbackWithoutMarkingEitherSourceDeleted() = runBlocking(Dispatchers.IO) {
        val app = expense("app", app = true)
        expenses.insertIngested(app)
        val failure = IllegalStateException("rollback fixture")
        val thrown = runCatching {
            database.withTransaction {
                expenses.insertIngested(expense("sms"))
                throw failure
            }
        }.exceptionOrNull()
        assertEquals(failure, thrown)
        assertEquals("app", expenses.getAllExpensesOnce().single().smsId)
        assertFalse(DeletedSmsTracker.isDeleted("app"))
        assertFalse(DeletedSmsTracker.isDeleted("sms"))
        // 동일 거래라는 판정은 rollback과 무관하지만 사용자가 삭제할 때만 제외한다.
        assertTrue(service.delete(TransactionTarget.Expense(expenses.getAllExpensesOnce().single().id)))
        assertTrue(DeletedSmsTracker.isDeleted("sms"))
    }

    @Test
    fun linksPropagateTransitivelyPersistAndClearWithoutTouchingUnrelatedIds() {
        DeletedSmsTracker.linkSameTransaction("app:bank_1", "sms_2")
        DeletedSmsTracker.linkSameTransaction("sms_2", "sms_3")
        DeletedSmsTracker.init(trackerContext())
        assertFalse(DeletedSmsTracker.isDeleted("app:bank_1"))
        DeletedSmsTracker.markDeleted("sms_3")
        DeletedSmsTracker.init(trackerContext())
        listOf("app:bank_1", "sms_2", "sms_3").forEach { assertTrue(DeletedSmsTracker.isDeleted(it)) }
        assertFalse(DeletedSmsTracker.isDeleted("unrelated"))
        DeletedSmsTracker.linkSameTransaction("sms_3", "late_alias")
        assertTrue(DeletedSmsTracker.isDeleted("late_alias"))
        DeletedSmsTracker.clear()
        DeletedSmsTracker.init(trackerContext())
        DeletedSmsTracker.markDeleted("sms_3")
        assertFalse(DeletedSmsTracker.isDeleted("app:bank_1"))
        assertFalse(DeletedSmsTracker.isDeleted("late_alias"))
    }

    @Test
    fun automaticIncomeChecksTombstoneAfterWaitingForRoomWriteTurn() = runBlocking(Dispatchers.IO) {
        val income = IncomeEntity(smsId = "waiting-income", amount = 12_000, type = "입금",
            description = "입금", isRecurring = false, dateTime = baseTime)
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = async {
            database.withTransaction {
                held.complete(Unit)
                release.await()
            }
        }
        withTimeout(5_000) { held.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            IncomeRepository(database.incomeDao()).insertIngested(income)
        }
        try {
            // 이미 DB 차례를 기다리는 자동 저장이 있으므로, 입구 검사만으로는 이 변경을 볼 수 없다.
            DeletedSmsTracker.markDeleted(requireNotNull(income.smsId))
            release.complete(Unit)
            withTimeout(5_000) { blocker.await() }
            assertEquals(null, withTimeout(5_000) { pending.await() })
            assertEquals(0, database.incomeDao().getIncomeCount())
        } finally {
            release.complete(Unit)
            pending.cancel()
            blocker.cancel()
        }
    }

    @Test
    fun concurrentDeletionCannotPersistAnOlderSnapshotLast() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val intercept = AtomicBoolean(true)
        val backing = preferences
        val heldSnapshots = mutableListOf<Set<String>>()
        val wrapped = object : SharedPreferences by backing {
            override fun edit(): SharedPreferences.Editor {
                val editor = backing.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                        if (key == "deleted_ids" && values != null) synchronized(heldSnapshots) {
                            heldSnapshots.add(values)
                        }
                        editor.putStringSet(key, values)
                        return this
                    }

                    override fun apply() {
                        if (intercept.compareAndSet(true, false)) {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                        editor.apply()
                    }
                }
            }
        }
        DeletedSmsTracker.init(trackerContext(wrapped))
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit { DeletedSmsTracker.markDeleted("first") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = workers.submit { DeletedSmsTracker.markDeleted("second") }
            try {
                second.get(250, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // 새 구현에서는 첫 저장이 끝날 때까지 두 번째 변경도 대기한다.
            }
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals(setOf("first"), heldSnapshots.first())
            assertEquals(setOf("first", "second"), backing.getStringSet("deleted_ids", emptySet()))
            DeletedSmsTracker.init(trackerContext())
            assertTrue(DeletedSmsTracker.isDeleted("first"))
            assertTrue(DeletedSmsTracker.isDeleted("second"))
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    private suspend fun assertBothSourcesStayDeleted(appFirst: Boolean) {
        val sms = expense("sms")
        val app = expense("app", app = true)
        expenses.insertIngested(if (appFirst) app else sms)
        val id = expenses.getAllExpensesOnce().single().id
        expenses.insertIngested(if (appFirst) sms else app)
        assertEquals(id, expenses.getAllExpensesOnce().single().id)
        assertEquals("sms", expenses.getAllExpensesOnce().single().smsId)
        assertFalse(DeletedSmsTracker.isDeleted("app"))
        assertTrue(service.delete(TransactionTarget.Expense(id)))
        DeletedSmsTracker.init(trackerContext())
        assertTrue(DeletedSmsTracker.isDeleted("sms"))
        assertTrue(DeletedSmsTracker.isDeleted("app"))
        assertEquals(ExpenseIngestionResult.Skipped, expenses.insertIngested(app))
        assertEquals(listOf(ExpenseIngestionResult.Skipped), expenses.insertAllIngested(listOf(sms)))
        assertEquals(0, expenses.getExpenseCount())
    }

    private fun trackerContext(storage: SharedPreferences = preferences) = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = storage
    }

    private fun expense(smsId: String, app: Boolean = false) = ExpenseEntity(
        amount = 12_000, storeName = "테스트상점", category = "기타", cardName = "우리",
        dateTime = baseTime, originalSms = "우리카드 1234 승인 테스트상점 12,000원", smsId = smsId,
        senderAddress = if (app) "app:com.wooricard.smartapp" else "15889955"
    )
}
