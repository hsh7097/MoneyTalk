package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.SmsBlockedSenderRepository
import com.sanha.moneytalk.core.database.dao.SmsBlockedSenderDao
import com.sanha.moneytalk.core.database.dao.SmsChannelProbeLogDao
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsReaderV2Test {

    private val reader = SmsReaderV2(
        smsBlockedSenderRepository = SmsBlockedSenderRepository(EmptySmsBlockedSenderDao()),
        channelProbeCollector = SmsChannelProbeCollector(EmptySmsChannelProbeLogDao())
    )

    @Test
    fun `rcs layout text is extracted as sms body`() {
        val layout = JsonObject().apply {
            addProperty("widget", "LinearLayout")
            add(
                "children",
                JsonArray().apply {
                    add(textView("스마일카드승인 하*현"))
                    add(textView("491,770원 일시불"))
                    add(textView("05/06 01:16 G마켓_스마일카드"))
                    add(textView("누적527,270원"))
                }
            )
        }
        val rawBody = JsonObject().apply {
            addProperty("layout", layout.toString())
        }.toString()

        val extracted = reader.extractRcsText(rawBody)

        assertEquals(
            listOf(
                "스마일카드승인 하*현",
                "491,770원 일시불",
                "05/06 01:16 G마켓_스마일카드",
                "누적527,270원"
            ).joinToString("\n"),
            extracted
        )
    }

    private class EmptySmsBlockedSenderDao : SmsBlockedSenderDao {
        override fun observeAll(): Flow<List<SmsBlockedSenderEntity>> = flowOf(emptyList())
        override suspend fun getAllAddresses(): List<String> = emptyList()
        override suspend fun insert(entity: SmsBlockedSenderEntity) = Unit
        override suspend fun deleteByAddress(address: String): Int = 0
    }

    private class EmptySmsChannelProbeLogDao : SmsChannelProbeLogDao {
        override suspend fun insert(log: SmsChannelProbeLogEntity) = Unit
        override suspend fun deleteOlderThan(minCreatedAt: Long): Int = 0
    }

    private fun textView(text: String): JsonObject {
        return JsonObject().apply {
            addProperty("widget", "TextView")
            addProperty("text", text)
        }
    }
}
