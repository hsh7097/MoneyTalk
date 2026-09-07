package com.sanha.moneytalk.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.TaskStackBuilder
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity

/** 거래별 PendingIntent 식별과 상세 화면 연결을 담당한다. */
internal object TransactionNotificationIntents {
    fun expense(context: Context, id: Long): PendingIntent = create(
        context, "expense", id, TransactionEditActivity.createIntent(context, expenseId = id)
    )

    fun income(context: Context, id: Long): PendingIntent = create(
        context, "income", id, TransactionEditActivity.createIntent(context, incomeId = id)
    )

    private fun create(context: Context, type: String, id: Long, intent: Intent): PendingIntent {
        require(id > 0L) { "A transaction notification requires a persisted row ID" }
        // extras는 PendingIntent 식별에 사용되지 않는다. 타입과 Long ID를 URI로 구분한다.
        intent.data = Uri.Builder().scheme("moneytalk").authority("transaction")
            .appendPath(type).appendPath(id.toString()).build()
        // 홈 → 상세의 back stack을 구성하고 새 ViewModel로 해당 거래를 로드한다.
        return requireNotNull(TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}
