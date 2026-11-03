package com.youyou.gaussmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 霍尔元件标记的默认位置（归一化屏幕坐标）。
 */
val DEFAULT_HALL_POS = Offset(0.5f, 0.5f)

/**
 * 全屏悬浮的霍尔元件位置标记。
 *
 * 把 `pointerInput` 挂在不随手指移动的父容器上，用屏幕绝对坐标跟踪拖动，
 * 避免 "pointer input 挂在被 .offset 的元素上导致坐标漂移" 的问题。
 * 触摸点在方框外时不消费事件，透传给下层。
 *
 * @param position 归一化坐标 (0..1, 0..1)
 * @param onPositionChange 拖动结束时回调
 */
@Composable
fun HallFloatingMarker(
    position: Offset,
    onPositionChange: (Offset) -> Unit,
    editable: Boolean,
    onDoneEditing: () -> Unit,
    modifier: Modifier = Modifier,
    boxColor: Color = Color(0xFFFF9800)
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val halfPx = with(density) { (HALL_BOX_DP / 2f).dp.toPx() }
        val hitPaddingPx = with(density) { 12.dp.toPx() } // 命中区域比视觉方框稍大

        // 位置以像素存储，拖动过程中频繁更新。
        // 父传入的归一化 position 变化时重建（如"复位"按钮）。
        var posPx by remember(position, widthPx, heightPx) {
            mutableStateOf(Offset(position.x * widthPx, position.y * heightPx))
        }

        // 双击检测：两次 down 发生在短时间+小位移内，视为"完成校准"
        var lastTapTimeMs by remember { mutableStateOf(0L) }
        var lastTapPos by remember { mutableStateOf(Offset.Zero) }
        val doubleTapWindowMs = 300L
        val doubleTapSlopPx = with(density) { 24.dp.toPx() }

        // 不可编辑时整个容器不接管触摸：所有点击透传到下层（Tab、按钮、滑块）
        val pointerModifier = if (editable) {
            Modifier.pointerInput(widthPx, heightPx) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull() ?: continue
                        if (!down.pressed || down.previousPressed) continue

                        val startPos = down.position
                        val inside = startPos.x in (posPx.x - halfPx - hitPaddingPx)..
                            (posPx.x + halfPx + hitPaddingPx) &&
                            startPos.y in (posPx.y - halfPx - hitPaddingPx)..
                            (posPx.y + halfPx + hitPaddingPx)
                        if (!inside) continue // 不消费，透传给下层

                        down.consume()
                        val pointerId = down.id
                        val downTime = System.currentTimeMillis()

                        // 判定双击：和上一次 down 时间短且位置相近
                        val isDoubleTap = (downTime - lastTapTimeMs) <= doubleTapWindowMs &&
                            kotlin.math.abs(startPos.x - lastTapPos.x) <= doubleTapSlopPx &&
                            kotlin.math.abs(startPos.y - lastTapPos.y) <= doubleTapSlopPx
                        if (isDoubleTap) {
                            lastTapTimeMs = 0L
                            lastTapPos = Offset.Zero
                            onDoneEditing()
                            // 等到抬起避免这次 down 被当成拖动
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val ch = ev.changes.firstOrNull { it.id == pointerId } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                            }
                            continue
                        }
                        lastTapTimeMs = downTime
                        lastTapPos = startPos

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                onPositionChange(
                                    Offset(posPx.x / widthPx, posPx.y / heightPx)
                                )
                                break
                            }
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                posPx = Offset(
                                    (posPx.x + delta.x).coerceIn(halfPx, widthPx - halfPx),
                                    (posPx.y + delta.y).coerceIn(halfPx, heightPx - halfPx)
                                )
                                change.consume()
                            }
                        }
                    }
                }
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pointerModifier)
        ) {
            // 画方框：不接管触摸，纯显示
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (posPx.x - halfPx).roundToInt(),
                            (posPx.y - halfPx).roundToInt()
                        )
                    }
                    .size(HALL_BOX_DP.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    drawRoundRect(
                        color = boxColor.copy(alpha = 0.14f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    drawRoundRect(
                        color = boxColor,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
                    )
                    drawCircle(
                        color = boxColor,
                        radius = 3.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
            }
        }
    }
}

private const val HALL_BOX_DP = 56f
