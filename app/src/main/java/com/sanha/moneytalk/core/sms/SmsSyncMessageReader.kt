package com.sanha.moneytalk.core.sms

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private class SmsPrimaryProviderReadException(message: String) : Exception(message)

/**
 * 동기화 대상 기간의 SMS/MMS/RCS 원본을 읽는 책임만 가진다.
 */
@Singleton
class SmsSyncMessageReader @Inject constructor(
    private val smsReaderV2: SmsReaderV2,
    @ApplicationContext private val context: Context
) {

    suspend fun read(range: Pair<Long, Long>): List<SmsInput> {
        val readResult = smsReaderV2.readAllMessagesByDateRange(
            context.contentResolver,
            range.first,
            range.second
        )
        if (!readResult.smsProviderReadSucceeded) {
            throw SmsPrimaryProviderReadException(context.getString(R.string.sync_sms_read_failed))
        }

        MoneyTalkLogger.i("syncSmsV2 SMS 읽기: ${readResult.messages.size}건")
        return readResult.messages
    }
}
