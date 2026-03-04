package com.sanha.moneytalk.core.model

import androidx.annotation.StringRes
import com.sanha.moneytalk.R

/**
 * 이체 방향 (입금/출금)
 *
 * - WITHDRAWAL: 출금 (돈이 나감, 금액에 `-` 표시)
 * - DEPOSIT: 입금 (돈이 들어옴, 금액에 `+` 표시)
 */
enum class TransferDirection(
    @StringRes val labelResId: Int,
    val dbValue: String
) {
    WITHDRAWAL(R.string.transfer_direction_withdrawal, "WITHDRAWAL"),
    DEPOSIT(R.string.transfer_direction_deposit, "DEPOSIT");

    companion object {
        fun fromDbValue(value: String): TransferDirection? {
            return entries.find { it.dbValue == value }
        }
    }
}
