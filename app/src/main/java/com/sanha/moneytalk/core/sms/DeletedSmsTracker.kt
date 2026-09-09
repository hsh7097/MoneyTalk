package com.sanha.moneytalk.core.sms

import android.content.Context
import android.content.SharedPreferences

/**
 * 사용자가 명시적으로 삭제한 거래의 smsId를 영구 추적.
 *
 * SMS 동기화(syncSmsV2)가 디바이스 SMS를 재읽기하여 삭제된 거래를
 * 다시 삽입하는 것을 방지한다.
 *
 * - 자동 수집이 동일 거래로 판정한 SMS/앱 알림 ID 연결을 함께 보존
 * - SharedPreferences: 앱 재시작 후에도 유지하며 변경/스냅샷/저장 순서를 직렬화
 *
 * Application.onCreate()에서 [init]을 호출하여 초기화한다.
 */
object DeletedSmsTracker {

    private const val PREFS_NAME = "deleted_sms_tracker"
    private const val KEY_DELETED_IDS = "deleted_ids"
    private const val KEY_SOURCE_LINKS = "source_links"

    private val deletedIds = mutableSetOf<String>()
    private val sourceLinks = mutableSetOf<String>()
    private val relatedIds = mutableMapOf<String, MutableSet<String>>()
    private var prefs: SharedPreferences? = null

    /** Application.onCreate()에서 호출하여 영구 저장소에서 복원 */
    @Synchronized
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getStringSet(KEY_DELETED_IDS, emptySet()) ?: emptySet()
        deletedIds.clear()
        deletedIds.addAll(saved)
        sourceLinks.clear()
        relatedIds.clear()
        prefs?.getStringSet(KEY_SOURCE_LINKS, emptySet()).orEmpty().forEach { encoded ->
            val separator = encoded.indexOf(':')
            val firstLength = encoded.substring(0, separator.coerceAtLeast(0)).toIntOrNull()
            if (firstLength != null && firstLength > 0 && firstLength < encoded.length - separator - 1) {
                val firstStart = separator + 1
                connect(
                    encoded.substring(firstStart, firstStart + firstLength),
                    encoded.substring(firstStart + firstLength)
                )
            }
        }
        // 구 버전의 정확한 ID 삭제 기록도 복원된 연결에 전파한다.
        val expanded = deletedIds.flatMap { connectedIds(it) }.toSet()
        if (deletedIds.addAll(expanded)) persist()
    }

    /** 의미 중복이 확정된 출처만 연결한다. 단순 행 ID 일치나 수동 변경에는 호출하지 않는다. */
    @Synchronized
    fun linkSameTransaction(firstSmsId: String?, secondSmsId: String?) {
        if (firstSmsId.isNullOrBlank() || secondSmsId.isNullOrBlank() || firstSmsId == secondSmsId) return
        if (!connect(firstSmsId, secondSmsId)) return
        val group = connectedIds(firstSmsId)
        if (group.any { it in deletedIds }) deletedIds.addAll(group)
        persist()
    }

    /** 삭제한 거래와 이미 확인된 다른 출처의 ID를 함께 등록한다. */
    @Synchronized
    fun markDeleted(smsId: String) {
        if (smsId.isNotBlank()) {
            if (deletedIds.addAll(connectedIds(smsId))) persist()
        }
    }

    /** 해당 smsId가 사용자에 의해 삭제되었는지 확인 */
    @Synchronized
    fun isDeleted(smsId: String): Boolean = smsId in deletedIds

    /** 전체 초기화 (설정 > 데이터 전체 삭제 시) */
    @Synchronized
    fun clear() {
        deletedIds.clear()
        sourceLinks.clear()
        relatedIds.clear()
        persist()
    }

    private fun connect(first: String, second: String): Boolean {
        val (left, right) = if (first <= second) first to second else second to first
        // 길이 접두어로 구분해 smsId 안의 콜론/밑줄에도 모호하지 않게 저장한다.
        if (!sourceLinks.add("${left.length}:$left$right")) return false
        relatedIds.getOrPut(left) { mutableSetOf() }.add(right)
        relatedIds.getOrPut(right) { mutableSetOf() }.add(left)
        return true
    }

    private fun connectedIds(smsId: String): Set<String> {
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(smsId)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (visited.add(current)) relatedIds[current]?.let { pending.addAll(it) }
        }
        return visited
    }

    // 호출부의 동일 monitor 안에서 변경, 독립 스냅샷 생성, apply 호출까지 순서를 보장한다.
    private fun persist() {
        prefs?.edit()
            ?.putStringSet(KEY_DELETED_IDS, deletedIds.toSet())
            ?.putStringSet(KEY_SOURCE_LINKS, sourceLinks.toSet())
            ?.apply()
    }
}
