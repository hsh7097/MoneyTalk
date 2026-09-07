package com.sanha.moneytalk.core.appfunctions

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/** 조회와 수정 진입점에서 공유하는 날짜, 카테고리와 결과 개수 계약이다. */
internal object MoneyTalkAppFunctionInputRules {
    fun parseDate(context: Context, date: String, endOfDay: Boolean): Long {
        return try {
            val parsed = DATE_FORMAT.parse(date)
                ?: throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
            Calendar.getInstance().apply {
                timeInMillis = parsed.time
                set(Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0)
                set(Calendar.MINUTE, if (endOfDay) 59 else 0)
                set(Calendar.SECOND, if (endOfDay) 59 else 0)
                set(Calendar.MILLISECOND, if (endOfDay) 999 else 0)
            }.timeInMillis
        } catch (exception: Exception) {
            throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
        }
    }

    fun categoryNamesIncludingCustom(categoryName: String): List<String> {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed)
        return if (category == Category.ETC && trimmed != Category.ETC.displayName) {
            listOf(trimmed)
        } else {
            category.displayNamesIncludingSub
        }
    }

    fun coerceLimit(limit: Int?, defaultLimit: Int = DEFAULT_LIMIT): Int {
        return max(limit ?: defaultLimit, MIN_LIMIT).coerceAtMost(MAX_LIMIT)
    }

    private const val DEFAULT_LIMIT = 20
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 200

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply {
        isLenient = false
    }
}
