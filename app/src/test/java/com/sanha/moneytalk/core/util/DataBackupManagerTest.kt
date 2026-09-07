package com.sanha.moneytalk.core.util

import com.google.gson.Gson
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataBackupManagerTest {

    @Test
    fun `수입 사용자 편집값과 원등록시각을 JSON 왕복 후 보존한다`() {
        val income = IncomeEntity(
            id = 41,
            smsId = "synthetic-income-1",
            amount = 45_000,
            type = "입금",
            source = "테스트 수입처",
            description = "테스트 입금",
            isRecurring = false,
            dateTime = 1_700_000_000_000L,
            originalSms = "테스트 입금 45,000원",
            senderAddress = "15880000",
            memo = "사용자 수입 메모",
            category = "사용자 수입 분류",
            createdAt = 1_700_000_003_000L
        )
        val manualIncome = income.copy(
            id = 42,
            smsId = null,
            originalSms = null,
            senderAddress = "",
            isRecurring = true,
            recurringDay = 25,
            createdAt = 1_700_000_004_000L
        )
        val json = DataBackupManager.createBackupJson(
            expenses = emptyList(),
            incomes = listOf(income, manualIncome),
            monthlyIncome = 0,
            monthStartDay = 1
        )
        val backup = Gson().fromJson(json, BackupData::class.java)

        val restored = DataBackupManager.convertToIncomeEntities(backup.incomes)

        assertEquals(listOf(income.copy(id = 0), manualIncome.copy(id = 0)), restored)
    }

    @Test
    fun `지출 메모와 원등록시각을 JSON 왕복 후 보존한다`() {
        val expense = ExpenseEntity(
            id = 51,
            amount = 12_000,
            storeName = "테스트 상점",
            category = "쇼핑",
            cardName = "테스트 카드",
            dateTime = 1_700_000_000_000L,
            originalSms = "테스트 상점 12,000원 승인",
            smsId = "synthetic-expense-1",
            senderAddress = "15880000",
            memo = "사용자 지출 메모",
            isFixed = true,
            isExcludedFromStats = true,
            createdAt = 1_700_000_002_000L
        )
        val json = DataBackupManager.createBackupJson(
            expenses = listOf(expense),
            incomes = emptyList(),
            monthlyIncome = 0,
            monthStartDay = 1
        )
        val backup = Gson().fromJson(json, BackupData::class.java)

        val restored = DataBackupManager.convertToExpenseEntities(backup.expenses).single()

        assertEquals(expense.copy(id = 0), restored)
    }

    @Test
    fun `JSON 백업에 사용자 설정과 거래 보정 필드를 포함한다`() {
        val json = DataBackupManager.createBackupJson(
            expenses = listOf(
                ExpenseEntity(
                    amount = 12_000,
                    storeName = "스타벅스",
                    category = "카페",
                    cardName = "신한",
                    dateTime = 1_700_000_000_000L,
                    originalSms = "스타벅스 12000원 입금",
                    smsId = "sms-1",
                    isFixed = true,
                    isExcludedFromStats = true,
                    transactionType = "TRANSFER",
                    transferDirection = "DEPOSIT"
                )
            ),
            incomes = listOf(
                IncomeEntity(
                    smsId = "income-sms-1",
                    amount = 8_200,
                    type = "환불",
                    description = "환불",
                    isRecurring = false,
                    dateTime = 1_700_000_001_000L,
                    senderAddress = "16449999",
                    originalSms = "출금취소 8200원"
                )
            ),
            monthlyIncome = 3_000_000,
            monthStartDay = 19,
            categoryMappings = listOf(
                CategoryMappingEntity(
                    storeName = "스타벅스",
                    category = "카페",
                    source = "user",
                    createdAt = 10L,
                    updatedAt = 20L
                )
            ),
            customCategories = listOf(
                CustomCategoryEntity(
                    displayName = "간식",
                    emoji = "S",
                    categoryType = "EXPENSE",
                    displayOrder = 3,
                    createdAt = 30L
                )
            ),
            storeRules = listOf(
                StoreRuleEntity(
                    keyword = "스타벅스",
                    category = "카페",
                    isFixed = true,
                    isExcludedFromStats = true,
                    createdAt = 40L
                )
            ),
            budgets = listOf(BudgetEntity(category = "카페", monthlyLimit = 100_000, yearMonth = "default")),
            ownedCards = listOf(
                OwnedCardEntity(
                    cardName = "신한",
                    isOwned = false,
                    firstSeenAt = 50L,
                    lastSeenAt = 60L,
                    seenCount = 3,
                    source = "manual"
                )
            ),
            smsExclusionKeywords = listOf(
                SmsExclusionKeywordEntity(keyword = "광고", source = "user", createdAt = 70L),
                SmsExclusionKeywordEntity(keyword = "기본", source = "default", createdAt = 80L)
            )
        )

        val backupData = Gson().fromJson(json, BackupData::class.java)

        assertEquals(2, backupData.version)
        assertEquals(3_000_000, backupData.settings.monthlyIncome)
        assertEquals(19, backupData.settings.monthStartDay)
        assertEquals("TRANSFER", backupData.expenses.single().transactionType)
        assertEquals("DEPOSIT", backupData.expenses.single().transferDirection)
        assertTrue(backupData.expenses.single().isFixed)
        assertTrue(backupData.expenses.single().isExcludedFromStats)
        assertEquals("income-sms-1", backupData.incomes.single().smsId)
        assertEquals(1, backupData.categoryMappings.size)
        assertEquals(1, backupData.customCategories.size)
        assertEquals(1, backupData.storeRules.size)
        assertEquals(1, backupData.budgets.size)
        assertEquals(1, backupData.ownedCards.size)
        assertEquals(listOf("광고"), backupData.smsExclusionKeywords.map { it.keyword })
    }

    @Test
    fun `기존 백업에 없는 거래 보정 필드는 기본값으로 복원한다`() {
        val legacyJson = """
            {
              "version": 1,
              "settings": {
                "monthlyIncome": 0,
                "monthStartDay": 1
              },
              "expenses": [
                {
                  "amount": 12000,
                  "storeName": "스타벅스",
                  "category": "카페",
                  "dateTime": 1700000000000,
                  "cardName": "신한",
                  "originalSms": "스타벅스 12000원 승인",
                  "smsId": "legacy-sms-1",
                  "memo": null,
                  "isExcludedFromStats": false
                }
              ],
              "incomes": [
                {
                  "amount": 8200,
                  "type": "환불",
                  "description": "환불",
                  "isRecurring": false,
                  "recurringDay": null,
                  "dateTime": 1700000001000,
                  "senderAddress": "16449999",
                  "originalSms": "출금취소 8200원"
                }
              ]
            }
        """.trimIndent()
        val backupData = Gson().fromJson(legacyJson, BackupData::class.java)

        val restoreStartedAt = System.currentTimeMillis()
        val expense = DataBackupManager.convertToExpenseEntities(backupData.expenses.orEmpty()).single()
        val income = DataBackupManager.convertToIncomeEntities(backupData.incomes.orEmpty()).single()
        val restoreFinishedAt = System.currentTimeMillis()

        assertEquals("EXPENSE", expense.transactionType)
        assertEquals("", expense.transferDirection)
        assertEquals(null, income.smsId)
        assertEquals("미분류", income.category)
        assertEquals("", income.source)
        assertEquals(null, income.memo)
        assertTrue(expense.createdAt in restoreStartedAt..restoreFinishedAt)
        assertTrue(income.createdAt in restoreStartedAt..restoreFinishedAt)
    }

    @Test
    fun `난독화된 릴리즈 백업 필드도 설정 데이터로 복원한다`() {
        val releaseJson = """
            {
              "version": 2,
              "settings": {
                "monthlyIncome": 0,
                "monthStartDay": 19
              },
              "categoryMappings": [
                { "a": "유튜브프리미엄", "b": "구독", "c": "user", "d": 10, "e": 20 }
              ],
              "customCategories": [
                { "a": "회사", "b": "B", "c": "EXPENSE", "d": 0, "e": 30 }
              ],
              "storeRules": [
                { "a": "유튜브프리미엄", "c": true, "e": 40 },
                { "a": "막", "b": "회사", "e": 50 }
              ],
              "ownedCards": [
                { "a": "신한", "b": false, "c": 60, "d": 70, "e": 3, "f": "manual" }
              ],
              "smsExclusionKeywords": [
                { "a": "카카오뱅크", "b": "user", "c": 80 }
              ]
            }
        """.trimIndent()

        val backupData = Gson().fromJson(releaseJson, BackupData::class.java)

        val categoryMapping = DataBackupManager.convertToCategoryMappingEntities(
            backupData.categoryMappings.orEmpty()
        ).single()
        val customCategory = DataBackupManager.convertToCustomCategoryEntities(
            backupData.customCategories.orEmpty()
        ).single()
        val storeRules = DataBackupManager.convertToStoreRuleEntities(
            backupData.storeRules.orEmpty()
        )
        val ownedCard = DataBackupManager.convertToOwnedCardEntities(
            backupData.ownedCards.orEmpty()
        ).single()
        val exclusionKeyword = DataBackupManager.convertToSmsExclusionKeywordEntities(
            backupData.smsExclusionKeywords.orEmpty()
        ).single()

        assertEquals("유튜브프리미엄", categoryMapping.storeName)
        assertEquals("구독", categoryMapping.category)
        assertEquals("회사", customCategory.displayName)
        assertEquals("EXPENSE", customCategory.categoryType)
        assertEquals("유튜브프리미엄", storeRules[0].keyword)
        assertEquals(true, storeRules[0].isFixed)
        assertEquals("막", storeRules[1].keyword)
        assertEquals("회사", storeRules[1].category)
        assertEquals("신한", ownedCard.cardName)
        assertEquals(false, ownedCard.isOwned)
        assertEquals("카카오뱅크", exclusionKeyword.keyword)
    }
}
