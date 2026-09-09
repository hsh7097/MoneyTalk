package com.sanha.moneytalk.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 수신 시 거래 알림을 표시하는 매니저.
 *
 * Application.onCreate()에서 [createNotificationChannel]을 호출하여 채널을 생성하고,
 * [SmsInstantProcessor]에서 파싱 완료 후 [showExpenseNotification] / [showIncomeNotification]을 호출한다.
 */
@Singleton
class SmsNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** v2: IMPORTANCE_HIGH로 변경 (헤드업 알림 지원). 기존 채널은 삭제 */
        const val CHANNEL_ID = "sms_transaction_v2"
        private const val OLD_CHANNEL_ID = "sms_transaction"
    }

    /** Application.onCreate()에서 호출하여 알림 채널 등록 */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // 기존 IMPORTANCE_DEFAULT 채널 삭제 (importance는 코드로 변경 불가)
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 지출 거래 알림 표시 */
    fun showExpenseNotification(
        expenseId: Long,
        amount: Int,
        storeName: String
    ) {
        showNotification("expense:$expenseId", storeName, amount,
            TransactionNotificationIntents.expense(context, expenseId),
            TransactionNotificationIntents.deleteExpense(context, expenseId),
            TransactionNotificationIntents.excludeExpense(context, expenseId))
    }

    /** 수입 거래 알림 표시 */
    fun showIncomeNotification(
        incomeId: Long,
        amount: Int,
        source: String,
        incomeType: String
    ) {
        val title = if (source.isNotBlank()) "$source $incomeType" else context.getString(R.string.notification_income_title)
        showNotification("income:$incomeId", title, amount,
            TransactionNotificationIntents.income(context, incomeId),
            TransactionNotificationIntents.deleteIncome(context, incomeId))
    }

    /** 단건 액션이 완료됐거나 이미 없는 거래의 알림만 정리한다. */
    fun cancelTransactionNotification(target: TransactionTarget) {
        val tag = when (target) {
            is TransactionTarget.Expense -> "expense:${target.id}"
            is TransactionTarget.Income -> "income:${target.id}"
        }
        context.getSystemService(NotificationManager::class.java).cancel(tag, 0)
    }

    /** 앱 진입 시 MoneyTalk 거래 알림을 정리 */
    fun clearTransactionNotifications() {
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.activeNotifications
                .filter { it.notification.channelId == CHANNEL_ID }
                .forEach { manager.cancel(it.tag, it.id) }
            return
        }

        manager.cancelAll()
    }

    private fun showNotification(
        tag: String,
        title: String,
        amount: Int,
        pendingIntent: PendingIntent,
        deleteIntent: PendingIntent,
        excludeIntent: PendingIntent? = null
    ) {
        val body = context.getString(R.string.notification_transaction_amount,
            NumberFormat.getNumberInstance(Locale.KOREA).format(amount))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .apply {
                if (excludeIntent != null) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            context.getString(R.string.transaction_edit_exclude_from_stats), excludeIntent
                        ).setAuthenticationRequired(true).build()
                    )
                }
            }
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_delete, context.getString(R.string.common_delete), deleteIntent
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE)
                    .setAuthenticationRequired(true)
                    .build()
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        // 프로세스 재시작이나 지출/수입 테이블의 동일 숫자 ID에도 다른 거래를 덮지 않는다.
        manager.notify(tag, 0, notification)
    }
}
