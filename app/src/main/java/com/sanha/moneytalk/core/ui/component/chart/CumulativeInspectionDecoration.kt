package com.sanha.moneytalk.core.ui.component.chart

import android.graphics.Paint
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration

/** Vico 2.0.0-alpha.28의 공개 Decoration/측정 좌표로 원본 금액의 선택 가이드와 점을 그린다. */
internal class CumulativeInspectionDecoration(
    private val geometry: CumulativeInspectionGeometry,
    private val selection: State<CumulativeChartInspection?>,
    private val guideColor: State<Int>,
    private val pointBackground: State<Int>
) : Decoration {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val direction = if (context.isLtr) 1f else -1f
        geometry.left = bounds.left
        geometry.right = bounds.right
        geometry.top = bounds.top
        geometry.bottom = bounds.bottom
        geometry.originX = (if (context.isLtr) bounds.left else bounds.right) +
            direction * context.horizontalDimensions.startPadding - context.scroll
        geometry.dayWidth = direction * context.horizontalDimensions.xSpacing / context.chartValues.xStep.toFloat()

        val selected = selection.value ?: return
        val x = geometry.originX + geometry.dayWidth * selected.dayIndex
        if (x < bounds.left || x > bounds.right) return
        val yRange = context.chartValues.getYRange(null)
        if (yRange.length <= 0.0) return
        paint.style = Paint.Style.STROKE
        paint.color = guideColor.value
        paint.strokeWidth = context.dpToPx(1f)
        context.canvas.drawLine(x, bounds.top, x, bounds.bottom, paint)
        // 값이 겹치면 현재 기간의 큰 점을 마지막에 그려 우선 보이게 한다.
        selected.values.asReversed().forEach { value ->
            // 숫자와 선택점 모두 모델의 원본 Long을 사용한다. 토글 애니메이션의 0원은 사용하지 않는다.
            val y = (bounds.bottom - ((value.amount.toDouble() - yRange.minY) / yRange.length) * bounds.height()).toFloat()
            if (y < bounds.top || y > bounds.bottom) return@forEach
            paint.style = Paint.Style.FILL
            paint.color = pointBackground.value
            context.canvas.drawCircle(x, y, context.dpToPx(if (value.isPrimary) 6f else 5f), paint)
            paint.color = value.color.toArgb()
            context.canvas.drawCircle(x, y, context.dpToPx(if (value.isPrimary) 4f else 3f), paint)
        }
    }
}
