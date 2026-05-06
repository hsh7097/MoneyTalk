package com.sanha.moneytalk.feature.transactionedit.ui

import java.util.Calendar
import java.util.TimeZone

internal object TransactionEditDateTimeMapper {

    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun toDatePickerMillis(timestamp: Long): Long {
        val localCal = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        return Calendar.getInstance(utcTimeZone).apply {
            clear()
            set(Calendar.YEAR, localCal.get(Calendar.YEAR))
            set(Calendar.MONTH, localCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun buildDateTime(datePickerMillis: Long, hour: Int, minute: Int): Long {
        val utcCal = Calendar.getInstance(utcTimeZone).apply {
            timeInMillis = datePickerMillis
        }

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
