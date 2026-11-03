package com.youyou.gaussmeter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class OnboardingStep {
    Welcome,         // 介绍
    HoldStill,       // 等手机静止
    LocateIntro,     // 看标记 Tab 的说明
    LocateCalibrate, // 回到读数 Tab 边扫磁铁边拖方框
    SetBaseline,     // 设基线
    Record           // 开始测量/记录
}

/**
 * 底部悬浮引导卡片。不是对话框，用户可以同时拖动方框、点按钮。
 *
 * @param step 当前步骤
 * @param isReady 当前步骤的条件是否已满足（如静止检测通过、基线已设）
 * @param onNext 用户点"下一步"
 * @param onSkip 用户点"跳过"关闭整个引导
 */
@Composable
fun OnboardingCard(
    step: OnboardingStep,
    isReady: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title: String
    val body: String
    val action: String
    when (step) {
        OnboardingStep.Welcome -> {
            title = "欢迎使用高斯计"
            body = "一共 4 步：静置手机 → 定位霍尔元件 → 设基线 → 开始测量。"
            action = "开始"
        }
        OnboardingStep.HoldStill -> {
            title = "① 请把手机平放在桌面上"
            body = if (isReady)
                "检测到手机已静止。"
            else
                "正在检测… 保持手机不动，让读数稳定下来。"
            action = "下一步"
        }
        OnboardingStep.LocateIntro -> {
            title = "② 定位霍尔元件"
            body = "屏幕上的橙色方框就是校准标记，可以拖到屏幕任意位置。点\"开始校准\"后" +
                "拿一块小磁铁贴着手机背面来回挪，三轴读数变化最大的地方就是磁力计位置。"
            action = "开始校准"
        }
        OnboardingStep.LocateCalibrate -> {
            title = "② 把方框拖到读数变化最大处"
            body = "一边扫磁铁一边观察 X/Y/Z 读数。读数跳得最厉害时，把橙色方框拖过去。校准完再继续。"
            action = "我拖好了"
        }
        OnboardingStep.SetBaseline -> {
            title = "③ 设基线"
            body = "移开磁铁，等读数稳定。切到\"基线\"Tab 点\"设基线\"，之后磁场变化超过阈值就会响。"
            action = "完成设置"
        }
        OnboardingStep.Record -> {
            title = "④ 开始测量"
            body = "需要留存数据时点\"开始记录\"，停止后能导出成 CSV。采样率决定每秒写几行：越低" +
                "文件越小，越高波形越完整，默认 10 Hz 够用。"
            action = "知道了"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = body,
                color = Color(0xFFDDDDDD),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) { Text("跳过引导") }
                Button(
                    onClick = onNext,
                    enabled = step != OnboardingStep.HoldStill || isReady
                ) {
                    Text(action)
                }
            }
        }
    }
}
