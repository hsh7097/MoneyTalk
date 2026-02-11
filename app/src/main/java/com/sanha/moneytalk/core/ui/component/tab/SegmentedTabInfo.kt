package com.sanha.moneytalk.core.ui.component.tab

import androidx.compose.ui.graphics.Color

/**
 * 세그먼트 탭 한 칸의 렌더링 정보.
 */
interface SegmentedTabInfo {
    /** 탭에 표시할 텍스트 */
    val label: String
    /** 현재 선택 상태 */
    val isSelected: Boolean
    /** 선택 시 배경색 */
    val selectedColor: Color
    /** 선택 시 텍스트색 */
    val selectedTextColor: Color
        get() = Color.White
}
