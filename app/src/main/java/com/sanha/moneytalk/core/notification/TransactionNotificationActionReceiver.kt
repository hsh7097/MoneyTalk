package com.sanha.moneytalk.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickPatch
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickUpdateResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 알림의 명시적 거래 액션만 처리한다. 알림을 쓸어 지우는 동작과는 연결하지 않는다. */
@AndroidEntryPoint
class TransactionNotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var transactionActions: TransactionQuickActionService
    @Inject lateinit var notifications: SmsNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val deleteTarget = TransactionNotificationIntents.deleteTarget(intent)
        val excludeTarget = TransactionNotificationIntents.excludeTarget(intent)
        val target = deleteTarget ?: excludeTarget ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completed = if (deleteTarget != null) {
                    // 이미 삭제된 거래도 안전하게 끝내고, 다른 알림은 유지한다.
                    transactionActions.delete(deleteTarget)
                    true
                } else {
                    when (transactionActions.update(target, TransactionQuickPatch(isExcludedFromStats = true))) {
                        is TransactionQuickUpdateResult.Updated, TransactionQuickUpdateResult.Missing -> true
                        TransactionQuickUpdateResult.InvalidCategory, TransactionQuickUpdateResult.UnsupportedField -> false
                    }
                }
                if (completed) notifications.cancelTransactionNotification(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 처리 실패 시 알림을 남겨 사용자가 다시 시도할 수 있게 한다.
                MoneyTalkLogger.e("[TransactionNotificationActionReceiver] 거래 액션 처리 실패", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
