package com.sanha.moneytalk.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.sanha.moneytalk.MainActivity
import com.sanha.moneytalk.R
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
        private var notificationId = 1000
    }

    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

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
        amount: Int,
        storeName: String,
        cardName: String
    ) {
        showNotification(storeName, "${numberFormat.format(amount)}원")
    }

    /** 수입 거래 알림 표시 */
    fun showIncomeNotification(
        amount: Int,
        source: String,
        incomeType: String
    ) {
        val title = if (source.isNotBlank()) "$source $incomeType" else "입금"
        showNotification(title, "${numberFormat.format(amount)}원")
    }

    /** 앱 진입 시 MoneyTalk 거래 알림을 정리 */
    fun clearTransactionNotifications() {
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.activeNotifications
                .filter { it.notification.channelId == CHANNEL_ID }
                .forEach { manager.cancel(it.id) }
            return
        }

        manager.cancelAll()
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
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
        manager.notify(notificationId++, notification)
    }
}
