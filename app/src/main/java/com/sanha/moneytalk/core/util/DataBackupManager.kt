package com.sanha.moneytalk.core.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
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
    val version: Int = 1,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date()),
    val settings: BackupSettings = BackupSettings(),
    val expenses: List<ExpenseBackup> = emptyList(),
    val incomes: List<IncomeBackup> = emptyList()
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
    val memo: String?
)

data class IncomeBackup(
    val amount: Int,
    val type: String,
    val description: String,
    val isRecurring: Boolean,
    val recurringDay: Int?,
    val dateTime: Long
)

/**
 * 데이터 백업/복원 매니저
 */
object DataBackupManager {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

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
        monthStartDay: Int
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
                    memo = expense.memo
                )
            },
            incomes = incomes.map { income ->
                IncomeBackup(
                    amount = income.amount,
                    type = income.type,
                    description = income.description,
                    isRecurring = income.isRecurring,
                    recurringDay = income.recurringDay,
                    dateTime = income.dateTime
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
        sb.appendLine("날짜,가맹점,카테고리,카드,금액")

        // 데이터
        expenses.forEach { expense ->
            sb.appendLine(
                "${escapeCsv(dateTimeFormat.format(Date(expense.dateTime)))}," +
                        "${escapeCsv(expense.storeName)}," +
                        "${escapeCsv(expense.category)}," +
                        "${escapeCsv(expense.cardName)}," +
                        "${expense.amount}"
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
        sb.appendLine("날짜,유형,설명,고정수입,입금일,금액")

        // 데이터
        incomes.forEach { income ->
            sb.appendLine(
                "${escapeCsv(dateTimeFormat.format(Date(income.dateTime)))}," +
                        "${escapeCsv(income.type)}," +
                        "${escapeCsv(income.description)}," +
                        "${income.isRecurring}," +
                        "${income.recurringDay ?: ""}," +
                        "${income.amount}"
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
        sb.appendLine("유형,날짜,이름,카테고리,카드/출처,메모,금액")

        // 지출 데이터
        expenses.forEach { expense ->
            sb.appendLine(
                "지출," +
                        "${escapeCsv(dateTimeFormat.format(Date(expense.dateTime)))}," +
                        "${escapeCsv(expense.storeName)}," +
                        "${escapeCsv(expense.category)}," +
                        "${escapeCsv(expense.cardName)}," +
                        "${escapeCsv(expense.memo ?: "")}," +
                        "-${expense.amount}"
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
                        "${escapeCsv(income.description)}," +
                        "+${income.amount}"
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
                memo = backup.memo,
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
                createdAt = System.currentTimeMillis()
            )
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
