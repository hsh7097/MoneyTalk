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
import java.util.*

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
    val dateTime: String,
    val cardName: String,
    val smsId: String
)

data class IncomeBackup(
    val amount: Int,
    val source: String,
    val dateTime: String,
    val note: String
)

/**
 * 데이터 백업/복원 매니저
 */
object DataBackupManager {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

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
                    smsId = expense.smsId
                )
            },
            incomes = incomes.map { income ->
                IncomeBackup(
                    amount = income.amount,
                    source = income.source,
                    dateTime = income.dateTime,
                    note = income.note
                )
            }
        )

        return gson.toJson(backupData)
    }

    /**
     * 백업 파일을 Uri에 저장
     */
    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        backupJson: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(backupJson.toByteArray(Charsets.UTF_8))
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
                dateTime = backup.dateTime,
                cardName = backup.cardName,
                smsId = backup.smsId,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun convertToIncomeEntities(backupIncomes: List<IncomeBackup>): List<IncomeEntity> {
        return backupIncomes.map { backup ->
            IncomeEntity(
                id = 0, // Room이 자동 생성
                amount = backup.amount,
                source = backup.source,
                dateTime = backup.dateTime,
                note = backup.note,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 백업 파일 이름 생성
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        return "moneytalk_backup_${dateFormat.format(Date())}.json"
    }
}
