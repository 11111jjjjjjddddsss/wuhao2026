package com.nongjiqianwen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun SessionApi.TodayAgriCard.isRenderableTodayAgriCard(): Boolean {
    val cardItems = items.orEmpty().take(3)
    return cardItems.size == 3 &&
        cardItems.all { item ->
            !item.title.isNullOrBlank() &&
                !item.summary.isNullOrBlank()
        }
}

@Composable
fun TodayAgriNewsText(
    card: SessionApi.TodayAgriCard,
    horizontalPadding: Dp = 18.dp,
    maxContentWidth: Dp = 560.dp,
    modifier: Modifier = Modifier
) {
    val content = card.toTodayAgriPlainText()
    if (content.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        ChatStreamingRenderer(
            content = content,
            renderMode = StreamingRenderMode.Settled,
            freshSuffixEnabled = false,
            showWaitingBall = false,
            streamingFreshStart = -1,
            streamingFreshEnd = -1,
            streamingFreshTick = 0,
            selectionEnabled = true,
            showDisclaimer = false,
            onStreamingContentBoundsChanged = null,
            expandToFullWidth = true,
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
        )
    }
}

fun SessionApi.TodayAgriCard.toTodayAgriPlainText(): String {
    val cardItems = items.orEmpty().take(3)
    if (cardItems.size != 3) return ""
    val dateText = todayAgriDateText(dateCn)
    return buildString {
        append("今日农情")
        if (!dateText.isNullOrBlank()) {
            append(" · ")
            append(dateText)
        }
        cardItems.forEachIndexed { index, item ->
            val title = item.title.orEmpty().trim()
            val summary = item.summary.orEmpty().trim()
            val source = item.source.orEmpty().trim()
            append("\n\n")
            append(index + 1)
            append(". ")
            if (title.isNotBlank()) {
                append(title)
                append("\n")
            }
            append(summary)
            if (source.isNotBlank()) {
                append("\n来源：")
                append(source)
            }
        }
    }
}

fun uiCopyPreviewTodayAgriCard(): SessionApi.TodayAgriCard =
    SessionApi.TodayAgriCard(
        dateCn = "20260613",
        title = "今日农情",
        items = listOf(
            SessionApi.TodayAgriCardItem(
                title = "华北麦区关注高温风",
                summary = "华北部分麦区进入收获和晾晒衔接期，天气变化会影响籽粒含水和收储节奏。建议结合当地预报安排抢收、摊晾和临时遮盖，避免成熟后长时间滞留田间。",
                source = "中国天气网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "早稻病虫进入巡查期",
                summary = "南方早稻陆续进入田管关键阶段，连续阴雨或高湿环境容易加重病虫发生。田间应重点看叶片、茎基部和田边低洼处，发现异常后再按当地植保建议处理。",
                source = "全国农技推广网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "蔬菜供应进入换茬期",
                summary = "多地露地蔬菜和设施蔬菜处在换茬衔接阶段，市场供应节奏会随天气和采收量波动。种植主体可关注本地批发走货、运输半径和短期降雨影响，合理安排采摘。",
                source = "农业农村部"
            )
        )
    )

fun uiCopyPreviewTodayAgriLongSummaryCard(): SessionApi.TodayAgriCard =
    SessionApi.TodayAgriCard(
        dateCn = "20260613",
        title = "今日农情",
        items = listOf(
            SessionApi.TodayAgriCardItem(
                title = "东北玉米苗情进入巡田期",
                summary = "东北部分玉米产区进入苗期管理阶段，近期温度和降水差异会影响出苗整齐度、低洼地块积水和苗弱苗黄情况。建议按地块实际苗情巡田，重点查看缺苗断垄、土壤墒情和草害变化，需要补救时先结合当地农技意见判断窗口期。",
                source = "中国农业信息网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "江淮水稻田管关注降雨",
                summary = "江淮及周边稻区在降雨增多时，要关注秧苗缓苗、田间水层和肥料流失情况。低洼田块应避免长时间深水，雨后及时看苗色、根系和叶片变化；若出现明显弱苗，应先排查积水、肥害和病虫因素，再做针对性处理。",
                source = "全国农技推广网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "夏季果园注意高温日灼",
                summary = "夏季果园进入高温强光阶段后，果面日灼、叶片失水和局部落果风险会上升。管理上可结合树势、负载量和水肥条件，适当保持土壤墒情，避免中午高温时段大幅操作；套袋、修剪和喷施措施应按当地品种和园况执行。",
                source = "中国气象局"
            )
        )
    )

private fun todayAgriDateText(dateCn: String?): String? {
    val digits = dateCn
        ?.trim()
        .orEmpty()
        .filter { it.isDigit() }
    if (digits.length != 8) return null
    val month = digits.substring(4, 6).trimStart('0').ifBlank { "0" }
    val day = digits.substring(6, 8).trimStart('0').ifBlank { "0" }
    return "${month}月${day}日"
}
