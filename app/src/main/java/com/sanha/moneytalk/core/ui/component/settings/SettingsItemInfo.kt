package com.sanha.moneytalk.core.ui.component.settings

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 설정 화면 아이템 UI에 필요한 데이터를 정의하는 Interface.
 * Composable은 이 Interface만 참조하여 렌더링한다.
 */
interface SettingsItemInfo {
    /** 좌측 아이콘 */
    val icon: ImageVector
    /** 제목 */
    val title: String
    /** 부제목 (빈 문자열이면 미표시) */
    val subtitle: String
        get() = ""
    /** 위험 액션 여부 (삭제 등) - 빨간색 강조 */
    val isDestructive: Boolean
        get() = false
}
