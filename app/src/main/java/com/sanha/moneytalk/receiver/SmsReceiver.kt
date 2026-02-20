package com.sanha.moneytalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sanha.moneytalk.core.util.DataRefreshEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 실시간 SMS 수신 BroadcastReceiver
 *
 * SMS 수신 감지 시 DataRefreshEvent.SMS_RECEIVED를 발행하여
 * HomeViewModel이 증분 동기화(syncIncremental)를 수행하도록 트리거합니다.
 *
 * 실제 파싱/저장은 HomeViewModel → SmsSyncCoordinator 파이프라인에서 처리하므로
 * Receiver는 이벤트 발행만 담당합니다.
 *
 * 앱 미실행 시에는 다음 앱 진입 시 syncIncremental이 자동으로 미처리 SMS를 포함합니다.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    @Inject
    lateinit var dataRefreshEvent: DataRefreshEvent

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "SMS 수신 감지 → 증분 동기화 트리거")
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
    }
}
