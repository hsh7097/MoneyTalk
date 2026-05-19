package com.sanha.moneytalk.core.util

import com.google.gson.Gson
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataBackupManagerTest {

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
                    isExcludedFromStats = true,
                    transactionType = "TRANSFER",
                    transferDirection = "DEPOSIT"
                )
            ),
            incomes = emptyList(),
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
        assertTrue(backupData.expenses.single().isExcludedFromStats)
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
              "incomes": []
            }
        """.trimIndent()
        val backupData = Gson().fromJson(legacyJson, BackupData::class.java)

        val expense = DataBackupManager.convertToExpenseEntities(backupData.expenses.orEmpty()).single()

        assertEquals("EXPENSE", expense.transactionType)
        assertEquals("", expense.transferDirection)
    }
}
