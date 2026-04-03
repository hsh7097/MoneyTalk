package com.sanha.moneytalk.core.sms

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 사용자가 명시적으로 삭제한 거래의 smsId를 영구 추적.
 *
 * SMS 동기화(syncSmsV2)가 디바이스 SMS를 재읽기하여 삭제된 거래를
 * 다시 삽입하는 것을 방지한다.
 *
 * - 인메모리 ConcurrentHashMap: 동기화 필터링 시 빠른 조회
 * - SharedPreferences: 앱 재시작 후에도 유지
 *
 * Application.onCreate()에서 [init]을 호출하여 초기화한다.
 */
object DeletedSmsTracker {

    private const val PREFS_NAME = "deleted_sms_tracker"
    private const val KEY_DELETED_IDS = "deleted_ids"

    private val deletedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var prefs: SharedPreferences? = null

    /** Application.onCreate()에서 호출하여 영구 저장소에서 복원 */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getStringSet(KEY_DELETED_IDS, emptySet()) ?: emptySet()
        deletedIds.addAll(saved)
    }

    /** 삭제된 거래의 smsId를 블랙리스트에 등록 (즉시 영구 저장) */
    fun markDeleted(smsId: String) {
        if (smsId.isNotBlank()) {
            deletedIds.add(smsId)
            persist()
        }
    }

    /** 해당 smsId가 사용자에 의해 삭제되었는지 확인 */
    fun isDeleted(smsId: String): Boolean = smsId in deletedIds

    /** 전체 초기화 (설정 > 데이터 전체 삭제 시) */
    fun clear() {
        deletedIds.clear()
        persist()
    }

    private fun persist() {
        prefs?.edit()
            ?.putStringSet(KEY_DELETED_IDS, deletedIds.toSet())
            ?.apply()
    }
}
