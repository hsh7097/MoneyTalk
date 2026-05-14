package com.sanha.moneytalk.core.notification

import com.sanha.moneytalk.core.database.dao.FinancialAppCandidateDao
import com.sanha.moneytalk.core.database.entity.FinancialAppCandidateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAppDiscoveryRepositoryTest {

    @Test
    fun `remote approved app is cached and used as supported app`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        val remote = FakeRemoteFinancialAppSource(
            approvedApps = listOf(
                RemoteFinancialApp(
                    packageName = REMOTE_CARD_PACKAGE,
                    displayName = "테스트카드",
                    appType = "CARD",
                    parserProfile = "COMMON_CARD",
                    enabled = true
                )
            )
        )
        val repository = createRepository(dao = dao, remote = remote)

        repository.refreshRemoteAppsIfNeeded(force = true)

        assertTrue(repository.isSupportedFinancialApp(REMOTE_CARD_PACKAGE))
        assertEquals(
            FinancialAppCandidateEntity.STATUS_SUPPORTED,
            dao.findByPackageName(REMOTE_CARD_PACKAGE)?.status
        )
    }

    @Test
    fun `remote removed app revokes local supported cache`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        dao.upsert(remoteSupportedEntity(REMOTE_CARD_PACKAGE))
        val remote = FakeRemoteFinancialAppSource(approvedApps = emptyList())
        val repository = createRepository(dao = dao, remote = remote)

        repository.refreshRemoteAppsIfNeeded(force = true)

        assertFalse(repository.isSupportedFinancialApp(REMOTE_CARD_PACKAGE))
        assertEquals(
            FinancialAppCandidateEntity.STATUS_REJECTED,
            dao.findByPackageName(REMOTE_CARD_PACKAGE)?.status
        )
    }

    @Test
    fun `remote load failure does not revoke local supported cache`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        dao.upsert(remoteSupportedEntity(REMOTE_CARD_PACKAGE))
        val remote = FakeRemoteFinancialAppSource(loadResult = null)
        val repository = createRepository(dao = dao, remote = remote)

        repository.refreshRemoteAppsIfNeeded(force = true)

        assertTrue(repository.isSupportedFinancialApp(REMOTE_CARD_PACKAGE))
        assertEquals(
            FinancialAppCandidateEntity.STATUS_SUPPORTED,
            dao.findByPackageName(REMOTE_CARD_PACKAGE)?.status
        )
    }

    @Test
    fun `existing candidate is touched without repeated llm or rtdb report`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        dao.upsert(
            FinancialAppCandidateEntity(
                packageName = UNKNOWN_PACKAGE,
                displayName = "기존앱",
                status = FinancialAppCandidateEntity.STATUS_REPORTED,
                source = FinancialAppCandidateEntity.SOURCE_LLM
            )
        )
        val remote = FakeRemoteFinancialAppSource()
        val analyzer = FakeFinancialAppCandidateAnalyzer(
            analysis = reportableAnalysis(UNKNOWN_PACKAGE, "새이름")
        )
        val repository = createRepository(dao = dao, remote = remote, analyzer = analyzer)

        repository.handleUnknownFinancialCandidate(
            packageName = UNKNOWN_PACKAGE,
            displayName = "새이름"
        )

        val cached = dao.findByPackageName(UNKNOWN_PACKAGE)
        assertEquals(FinancialAppCandidateEntity.STATUS_REPORTED, cached?.status)
        assertEquals("새이름", cached?.displayName)
        assertEquals(0, analyzer.callCount)
        assertEquals(0, remote.reportCount)
    }

    @Test
    fun `debug report success caches candidate as reported`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        val remote = FakeRemoteFinancialAppSource(canReport = true)
        val analyzer = FakeFinancialAppCandidateAnalyzer(
            analysis = reportableAnalysis(UNKNOWN_PACKAGE, "테스트페이")
        )
        val repository = createRepository(dao = dao, remote = remote, analyzer = analyzer)

        repository.handleUnknownFinancialCandidate(
            packageName = UNKNOWN_PACKAGE,
            displayName = "테스트페이"
        )

        assertEquals(1, analyzer.callCount)
        assertEquals(1, remote.reportCount)
        assertEquals(
            FinancialAppCandidateEntity.STATUS_REPORTED,
            dao.findByPackageName(UNKNOWN_PACKAGE)?.status
        )
    }

    @Test
    fun `release report disabled caches candidate without remote report`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        val remote = FakeRemoteFinancialAppSource(canReport = false)
        val analyzer = FakeFinancialAppCandidateAnalyzer(
            analysis = reportableAnalysis(UNKNOWN_PACKAGE, "테스트페이")
        )
        val repository = createRepository(dao = dao, remote = remote, analyzer = analyzer)

        repository.handleUnknownFinancialCandidate(
            packageName = UNKNOWN_PACKAGE,
            displayName = "테스트페이"
        )

        assertEquals(1, analyzer.callCount)
        assertEquals(0, remote.reportCount)
        assertEquals(
            FinancialAppCandidateEntity.STATUS_REPORT_DISABLED,
            dao.findByPackageName(UNKNOWN_PACKAGE)?.status
        )
    }

    @Test
    fun `non reportable llm result is cached as rejected`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        val remote = FakeRemoteFinancialAppSource(canReport = true)
        val analyzer = FakeFinancialAppCandidateAnalyzer(
            analysis = FinancialAppAnalysis(
                packageName = UNKNOWN_PACKAGE,
                displayName = "쇼핑앱",
                classification = FinancialAppAnalysis.CLASSIFICATION_SHOPPING_OR_MESSENGER,
                appType = "SHOPPING",
                parserProfile = "NONE",
                confidence = 0.95f,
                reason = "쇼핑 앱"
            )
        )
        val repository = createRepository(dao = dao, remote = remote, analyzer = analyzer)

        repository.handleUnknownFinancialCandidate(
            packageName = UNKNOWN_PACKAGE,
            displayName = "쇼핑앱"
        )

        assertEquals(1, analyzer.callCount)
        assertEquals(0, remote.reportCount)
        assertEquals(
            FinancialAppCandidateEntity.STATUS_REJECTED,
            dao.findByPackageName(UNKNOWN_PACKAGE)?.status
        )
    }

    @Test
    fun `static registry package is supported without local cache`() = runSuspendTest {
        val dao = FakeFinancialAppCandidateDao()
        val repository = createRepository(dao = dao)

        assertTrue(repository.isSupportedFinancialApp("com.kakaobank.channel"))
        assertNull(dao.findByPackageName("com.kakaobank.channel"))
    }

    private fun createRepository(
        dao: FakeFinancialAppCandidateDao = FakeFinancialAppCandidateDao(),
        remote: FakeRemoteFinancialAppSource = FakeRemoteFinancialAppSource(),
        analyzer: FakeFinancialAppCandidateAnalyzer = FakeFinancialAppCandidateAnalyzer()
    ): FinancialAppDiscoveryRepository {
        return FinancialAppDiscoveryRepository(
            candidateDao = dao,
            remoteRepository = remote,
            llmAnalyzer = analyzer
        )
    }

    private fun remoteSupportedEntity(packageName: String): FinancialAppCandidateEntity {
        return FinancialAppCandidateEntity(
            packageName = packageName,
            displayName = "테스트카드",
            status = FinancialAppCandidateEntity.STATUS_SUPPORTED,
            appType = "CARD",
            parserProfile = "COMMON_CARD",
            confidence = 1f,
            reason = "RTDB 승인 금융앱",
            source = FinancialAppCandidateEntity.SOURCE_RTDB
        )
    }

    private fun reportableAnalysis(
        packageName: String,
        displayName: String
    ): FinancialAppAnalysis {
        return FinancialAppAnalysis(
            packageName = packageName,
            displayName = displayName,
            classification = FinancialAppAnalysis.CLASSIFICATION_FINANCIAL_NEEDS_TEST,
            appType = "PAY",
            parserProfile = "COMMON_PAY",
            confidence = 0.9f,
            reason = "간편결제 후보"
        )
    }

    private fun runSuspendTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    private class FakeFinancialAppCandidateDao : FinancialAppCandidateDao {
        private val entities = LinkedHashMap<String, FinancialAppCandidateEntity>()

        override suspend fun findByPackageName(packageName: String): FinancialAppCandidateEntity? {
            return entities[packageName]
        }

        override suspend fun getPackageNamesByStatus(status: String): List<String> {
            return entities.values
                .filter { it.status == status }
                .map { it.packageName }
        }

        override suspend fun getBySourceAndStatus(
            source: String,
            status: String
        ): List<FinancialAppCandidateEntity> {
            return entities.values.filter { it.source == source && it.status == status }
        }

        override suspend fun upsert(entity: FinancialAppCandidateEntity) {
            entities[entity.packageName] = entity
        }

        override suspend fun touch(
            packageName: String,
            displayName: String,
            lastSeenAt: Long
        ) {
            val existing = entities[packageName] ?: return
            entities[packageName] = existing.copy(
                displayName = displayName,
                lastSeenAt = lastSeenAt,
                updatedAt = lastSeenAt
            )
        }
    }

    private class FakeRemoteFinancialAppSource(
        private val canReport: Boolean = true,
        private val loadResult: List<RemoteFinancialApp>? = emptyList(),
        approvedApps: List<RemoteFinancialApp>? = null
    ) : RemoteFinancialAppSource {
        var reportCount: Int = 0
            private set

        private val result = approvedApps ?: loadResult

        override fun canReportCandidates(): Boolean = canReport

        override suspend fun loadApprovedApps(): List<RemoteFinancialApp>? = result

        override suspend fun reportCandidate(analysis: FinancialAppAnalysis): Boolean {
            reportCount += 1
            return true
        }
    }

    private class FakeFinancialAppCandidateAnalyzer(
        private val analysis: FinancialAppAnalysis? = null
    ) : FinancialAppCandidateAnalyzer {
        var callCount: Int = 0
            private set

        override suspend fun analyze(
            packageName: String,
            displayName: String
        ): FinancialAppAnalysis? {
            callCount += 1
            return analysis
        }
    }

    companion object {
        private const val REMOTE_CARD_PACKAGE = "com.example.remote.card"
        private const val UNKNOWN_PACKAGE = "com.example.unknown.pay"
    }
}
