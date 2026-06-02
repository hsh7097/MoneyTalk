package com.sanha.moneytalk.core.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 내보내기 필터 옵션
 */
data class ExportFilter(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val cardNames: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val includeExpenses: Boolean = true,
    val includeIncomes: Boolean = true
)

/**
 * 내보내기 형식
 */
enum class ExportFormat {
    JSON,
    CSV
}

/**
 * 백업 데이터 모델
 */
data class BackupData(
    val version: Int = 2,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date()),
    val settings: BackupSettings = BackupSettings(),
    val expenses: List<ExpenseBackup> = emptyList(),
    val incomes: List<IncomeBackup> = emptyList(),
    val categoryMappings: List<CategoryMappingBackup> = emptyList(),
    val customCategories: List<CustomCategoryBackup> = emptyList(),
    val storeRules: List<StoreRuleBackup> = emptyList(),
    val budgets: List<BudgetBackup> = emptyList(),
    val ownedCards: List<OwnedCardBackup> = emptyList(),
    val smsExclusionKeywords: List<SmsExclusionKeywordBackup> = emptyList()
)

data class BackupSettings(
    val monthlyIncome: Int = 0,
    val monthStartDay: Int = 1
    // API 키는 보안상 백업에 포함하지 않음
)

data class ExpenseBackup(
    val amount: Int,
    val storeName: String,
    val category: String,
    val dateTime: Long,
    val cardName: String,
    val originalSms: String,
    val smsId: String,
    val senderAddress: String = "",
    val memo: String?,
    val isExcludedFromStats: Boolean = false,
    val transactionType: String = "EXPENSE",
    val transferDirection: String = ""
)

data class IncomeBackup(
    val amount: Int,
    val type: String,
    val description: String,
    val isRecurring: Boolean,
    val recurringDay: Int?,
    val dateTime: Long,
    val senderAddress: String = "",
    val originalSms: String? = null
)

data class CategoryMappingBackup(
    val storeName: String,
    val category: String,
    val source: String = "local",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CustomCategoryBackup(
    val displayName: String,
    val emoji: String,
    val categoryType: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class StoreRuleBackup(
    val keyword: String,
    val category: String? = null,
    val isFixed: Boolean? = null,
    val isExcludedFromStats: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BudgetBackup(
    val category: String,
    val monthlyLimit: Int,
    val yearMonth: String = "default"
)

data class OwnedCardBackup(
    val cardName: String,
    val isOwned: Boolean = true,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val seenCount: Int = 1,
    val source: String = "sms_sync"
)

data class SmsExclusionKeywordBackup(
    val keyword: String,
    val source: String = "user",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 데이터 백업/복원 매니저
 */
object DataBackupManager {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    private fun String?.orDefaultIfBlank(defaultValue: String): String {
        return if (isNullOrBlank()) defaultValue else this
    }

    /**
     * 필터를 적용한 지출 데이터 필터링
     */
    fun filterExpenses(
        expenses: List<ExpenseEntity>,
        filter: ExportFilter
    ): List<ExpenseEntity> {
        var filtered = expenses

        // 날짜 필터
        if (filter.startDate != null) {
            filtered = filtered.filter {
                it.dateTime >= filter.startDate
            }
        }
        if (filter.endDate != null) {
            filtered = filtered.filter {
                it.dateTime <= filter.endDate
            }
        }

        // 카드 필터
        if (filter.cardNames.isNotEmpty()) {
            filtered = filtered.filter { it.cardName in filter.cardNames }
        }

        // 카테고리 필터
        if (filter.categories.isNotEmpty()) {
            filtered = filtered.filter { it.category in filter.categories }
        }

        return filtered
    }

    /**
     * 필터를 적용한 수입 데이터 필터링
     */
    fun filterIncomes(
        incomes: List<IncomeEntity>,
        filter: ExportFilter
    ): List<IncomeEntity> {
        var filtered = incomes

        // 날짜 필터
        if (filter.startDate != null) {
            filtered = filtered.filter {
                it.dateTime >= filter.startDate
            }
        }
        if (filter.endDate != null) {
            filtered = filtered.filter {
                it.dateTime <= filter.endDate
            }
        }

        return filtered
    }

    private fun parseDateTime(dateTimeStr: String): Date? {
        return try {
            dateTimeFormat.parse(dateTimeStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 백업 데이터를 JSON 문자열로 변환
     */
    fun createBackupJson(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        monthlyIncome: Int,
        monthStartDay: Int,
        categoryMappings: List<CategoryMappingEntity> = emptyList(),
        customCategories: List<CustomCategoryEntity> = emptyList(),
        storeRules: List<StoreRuleEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        ownedCards: List<OwnedCardEntity> = emptyList(),
        smsExclusionKeywords: List<SmsExclusionKeywordEntity> = emptyList()
    ): String {
        val backupData = BackupData(
            settings = BackupSettings(
                monthlyIncome = monthlyIncome,
                monthStartDay = monthStartDay
            ),
            expenses = expenses.map { expense ->
                ExpenseBackup(
                    amount = expense.amount,
                    storeName = expense.storeName,
                    category = expense.category,
                    dateTime = expense.dateTime,
                    cardName = expense.cardName,
                    originalSms = expense.originalSms,
                    smsId = expense.smsId,
                    senderAddress = expense.senderAddress,
                    memo = expense.memo,
                    isExcludedFromStats = expense.isExcludedFromStats,
                    transactionType = expense.transactionType,
                    transferDirection = expense.transferDirection
                )
            },
            incomes = incomes.map { income ->
                IncomeBackup(
                    amount = income.amount,
                    type = income.type,
                    description = income.description,
                    isRecurring = income.isRecurring,
                    recurringDay = income.recurringDay,
                    dateTime = income.dateTime,
                    senderAddress = income.senderAddress,
                    originalSms = income.originalSms
                )
            },
            categoryMappings = categoryMappings.map { mapping ->
                CategoryMappingBackup(
                    storeName = mapping.storeName,
                    category = mapping.category,
                    source = mapping.source,
                    createdAt = mapping.createdAt,
                    updatedAt = mapping.updatedAt
                )
            },
            customCategories = customCategories.map { category ->
                CustomCategoryBackup(
                    displayName = category.displayName,
                    emoji = category.emoji,
                    categoryType = category.categoryType,
                    displayOrder = category.displayOrder,
                    createdAt = category.createdAt
                )
            },
            storeRules = storeRules.map { rule ->
                StoreRuleBackup(
                    keyword = rule.keyword,
                    category = rule.category,
                    isFixed = rule.isFixed,
                    isExcludedFromStats = rule.isExcludedFromStats,
                    createdAt = rule.createdAt
                )
            },
            budgets = budgets.map { budget ->
                BudgetBackup(
                    category = budget.category,
                    monthlyLimit = budget.monthlyLimit,
                    yearMonth = budget.yearMonth
                )
            },
            ownedCards = ownedCards.map { card ->
                OwnedCardBackup(
                    cardName = card.cardName,
                    isOwned = card.isOwned,
                    firstSeenAt = card.firstSeenAt,
                    lastSeenAt = card.lastSeenAt,
                    seenCount = card.seenCount,
                    source = card.source
                )
            },
            smsExclusionKeywords = smsExclusionKeywords
                .filter { it.source != "default" }
                .map { keyword ->
                    SmsExclusionKeywordBackup(
                        keyword = keyword.keyword,
                        source = keyword.source,
                        createdAt = keyword.createdAt
                    )
                }
        )

        return gson.toJson(backupData)
    }

    /**
     * 지출 데이터를 CSV 문자열로 변환
     */
    fun createExpensesCsv(expenses: List<ExpenseEntity>): String {
        val sb = StringBuilder()

        // BOM for Excel UTF-8 recognition
        sb.append('\uFEFF')

        // 헤더
        sb.appendLine("날짜,가맹점,카테고리,카드,전화번호,금액,통계제외,메모,문자원본")

        // 데이터
        expenses.forEach { expense ->
            sb.appendLine(
                "${escapeCsv(dateTimeFormat.format(Date(expense.dateTime)))}," +
                        "${escapeCsv(expense.storeName)}," +
                        "${escapeCsv(expense.category)}," +
                        "${escapeCsv(expense.cardName)}," +
                        "${escapeCsv(expense.senderAddress)}," +
                        "${expense.amount}," +
                        "${expense.isExcludedFromStats}," +
                        "${escapeCsv(expense.memo ?: "")}," +
                        escapeCsv(expense.originalSms)
            )
        }

        return sb.toString()
    }

    /**
     * 수입 데이터를 CSV 문자열로 변환
     */
    fun createIncomesCsv(incomes: List<IncomeEntity>): String {
        val sb = StringBuilder()

        // BOM for Excel UTF-8 recognition
        sb.append('\uFEFF')

        // 헤더
        sb.appendLine("날짜,유형,설명,전화번호,고정수입,입금일,금액,문자원본")

        // 데이터
        incomes.forEach { income ->
            sb.appendLine(
                        "${escapeCsv(dateTimeFormat.format(Date(income.dateTime)))}," +
                        "${escapeCsv(income.type)}," +
                        "${escapeCsv(income.description)}," +
                        "${escapeCsv(income.senderAddress)}," +
                        "${income.isRecurring}," +
                        "${income.recurringDay ?: ""}," +
                        "${income.amount}," +
                        escapeCsv(income.originalSms ?: "")
            )
        }

        return sb.toString()
    }

    /**
     * 전체 데이터를 CSV 문자열로 변환 (지출 + 수입 통합)
     */
    fun createCombinedCsv(expenses: List<ExpenseEntity>, incomes: List<IncomeEntity>): String {
        val sb = StringBuilder()

        // BOM for Excel UTF-8 recognition
        sb.append('\uFEFF')

        // 헤더
        sb.appendLine("유형,날짜,이름,카테고리,카드/출처,전화번호,메모,금액,통계제외,문자원본")

        // 지출 데이터
        expenses.forEach { expense ->
            sb.appendLine(
                "지출," +
                        "${escapeCsv(dateTimeFormat.format(Date(expense.dateTime)))}," +
                        "${escapeCsv(expense.storeName)}," +
                        "${escapeCsv(expense.category)}," +
                        "${escapeCsv(expense.cardName)}," +
                        "${escapeCsv(expense.senderAddress)}," +
                        "${escapeCsv(expense.memo ?: "")}," +
                        "-${expense.amount}," +
                        "${expense.isExcludedFromStats}," +
                        escapeCsv(expense.originalSms)
            )
        }

        // 수입 데이터
        incomes.forEach { income ->
            sb.appendLine(
                "수입," +
                        "${escapeCsv(dateTimeFormat.format(Date(income.dateTime)))}," +
                        "${escapeCsv(income.type)}," +
                        "," +
                        "," +
                        "${escapeCsv(income.senderAddress)}," +
                        "${escapeCsv(income.description)}," +
                        "+${income.amount}," +
                        "false," +
                        escapeCsv(income.originalSms ?: "")
            )
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * 백업 파일을 Uri에 저장
     */
    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uri에서 백업 데이터 읽기
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri
    ): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("파일을 읽을 수 없습니다")

            val backupData = gson.fromJson(jsonString, BackupData::class.java)
            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * BackupData를 Entity 리스트로 변환
     */
    fun convertToExpenseEntities(backupExpenses: List<ExpenseBackup>): List<ExpenseEntity> {
        return backupExpenses.map { backup ->
            ExpenseEntity(
                id = 0, // Room이 자동 생성
                amount = backup.amount,
                storeName = backup.storeName,
                category = backup.category,
                cardName = backup.cardName,
                dateTime = backup.dateTime,
                originalSms = backup.originalSms,
                smsId = backup.smsId,
                senderAddress = backup.senderAddress.orEmpty(),
                memo = backup.memo,
                isExcludedFromStats = backup.isExcludedFromStats,
                transactionType = backup.transactionType.orDefaultIfBlank("EXPENSE"),
                transferDirection = backup.transferDirection.orEmpty(),
                createdAt = System.currentTimeMillis()
            )
        }
    }

    fun convertToIncomeEntities(backupIncomes: List<IncomeBackup>): List<IncomeEntity> {
        return backupIncomes.map { backup ->
            IncomeEntity(
                id = 0, // Room이 자동 생성
                amount = backup.amount,
                type = backup.type,
                description = backup.description,
                isRecurring = backup.isRecurring,
                recurringDay = backup.recurringDay,
                dateTime = backup.dateTime,
                senderAddress = backup.senderAddress.orEmpty(),
                originalSms = backup.originalSms,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    fun convertToCategoryMappingEntities(
        backups: List<CategoryMappingBackup>
    ): List<CategoryMappingEntity> {
        return backups.mapNotNull { backup ->
            val storeName = backup.storeName.trim()
            val category = backup.category.trim()
            if (storeName.isBlank() || category.isBlank()) {
                null
            } else {
                CategoryMappingEntity(
                    id = 0,
                    storeName = storeName,
                    category = category,
                    source = backup.source.orDefaultIfBlank("local"),
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt
                )
            }
        }
    }

    fun convertToCustomCategoryEntities(
        backups: List<CustomCategoryBackup>
    ): List<CustomCategoryEntity> {
        return backups.mapNotNull { backup ->
            val displayName = backup.displayName.trim()
            if (displayName.isBlank()) {
                null
            } else {
                CustomCategoryEntity(
                    id = 0,
                    displayName = displayName,
                    emoji = backup.emoji.orDefaultIfBlank("\uD83D\uDCE6"),
                    categoryType = backup.categoryType.orDefaultIfBlank("EXPENSE"),
                    displayOrder = backup.displayOrder,
                    createdAt = backup.createdAt
                )
            }
        }
    }

    fun convertToStoreRuleEntities(backups: List<StoreRuleBackup>): List<StoreRuleEntity> {
        return backups.mapNotNull { backup ->
            val keyword = backup.keyword.trim()
            if (keyword.isBlank()) {
                null
            } else {
                StoreRuleEntity(
                    id = 0,
                    keyword = keyword,
                    category = backup.category,
                    isFixed = backup.isFixed,
                    isExcludedFromStats = backup.isExcludedFromStats,
                    createdAt = backup.createdAt
                )
            }
        }
    }

    fun convertToBudgetEntities(backups: List<BudgetBackup>): List<BudgetEntity> {
        return backups.mapNotNull { backup ->
            val category = backup.category.trim()
            if (category.isBlank() || backup.monthlyLimit <= 0) {
                null
            } else {
                BudgetEntity(
                    category = category,
                    monthlyLimit = backup.monthlyLimit,
                    yearMonth = backup.yearMonth.orDefaultIfBlank("default")
                )
            }
        }
    }

    fun convertToOwnedCardEntities(backups: List<OwnedCardBackup>): List<OwnedCardEntity> {
        return backups.mapNotNull { backup ->
            val cardName = backup.cardName.trim()
            if (cardName.isBlank()) {
                null
            } else {
                OwnedCardEntity(
                    cardName = cardName,
                    isOwned = backup.isOwned,
                    firstSeenAt = backup.firstSeenAt,
                    lastSeenAt = backup.lastSeenAt,
                    seenCount = backup.seenCount.coerceAtLeast(0),
                    source = backup.source.orDefaultIfBlank("sms_sync")
                )
            }
        }
    }

    fun convertToSmsExclusionKeywordEntities(
        backups: List<SmsExclusionKeywordBackup>
    ): List<SmsExclusionKeywordEntity> {
        return backups.mapNotNull { backup ->
            val keyword = backup.keyword.trim().lowercase()
            if (keyword.isBlank() || backup.source == "default") {
                null
            } else {
                SmsExclusionKeywordEntity(
                    keyword = keyword,
                    source = backup.source.orDefaultIfBlank("user"),
                    createdAt = backup.createdAt
                )
            }
        }
    }

    /**
     * 백업 파일 이름 생성
     */
    fun generateBackupFileName(format: ExportFormat = ExportFormat.JSON): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        val extension = when (format) {
            ExportFormat.JSON -> "json"
            ExportFormat.CSV -> "csv"
        }
        return "moneytalk_backup_${dateFormat.format(Date())}.$extension"
    }
}
