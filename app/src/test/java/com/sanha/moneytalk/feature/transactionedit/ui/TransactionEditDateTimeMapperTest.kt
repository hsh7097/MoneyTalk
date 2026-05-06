package com.sanha.moneytalk.feature.transactionedit.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TransactionEditDateTimeMapperTest {

    @Test
    fun existingEarlyMorningTimestamp_doesNotMoveToPreviousDayOnRepeatedSave() {
        withTimeZone("Asia/Seoul") {
            val original = localTime(2026, Calendar.MAY, 6, 1, 30)

            val firstSave = TransactionEditDateTimeMapper.buildDateTime(
                TransactionEditDateTimeMapper.toDatePickerMillis(original),
                hour = 1,
                minute = 30
            )
            val secondSave = TransactionEditDateTimeMapper.buildDateTime(
                TransactionEditDateTimeMapper.toDatePickerMillis(firstSave),
                hour = 1,
                minute = 30
            )

            assertEquals(original, firstSave)
            assertEquals(original, secondSave)
        }
    }

    @Test
    fun existingDaytimeTimestamp_keepsSameLocalDateTimeOnSave() {
        withTimeZone("Asia/Seoul") {
            val original = localTime(2026, Calendar.MAY, 6, 12, 15)

            val saved = TransactionEditDateTimeMapper.buildDateTime(
                TransactionEditDateTimeMapper.toDatePickerMillis(original),
                hour = 12,
                minute = 15
            )

            assertEquals(original, saved)
        }
    }

    private fun withTimeZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun localTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
    }
}
