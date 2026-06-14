package com.nongjiqianwen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun SessionApi.TodayAgriCard.isRenderableTodayAgriCard(): Boolean {
    val cardItems = items.orEmpty().take(3)
    return cardItems.size == 3 &&
        cardItems.all { item ->
            !item.title.isNullOrBlank() &&
                !item.summary.isNullOrBlank()
        }
}

@Composable
fun TodayAgriNewsCard(
    card: SessionApi.TodayAgriCard,
    horizontalPadding: Dp = 12.dp,
    maxCardWidth: Dp = 560.dp,
    modifier: Modifier = Modifier
) {
    val items = card.items.orEmpty().take(3)
    if (items.size != 3) return
    val dateText = todayAgriDateText(card.dateCn)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.8.dp, Color.Black),
            modifier = Modifier
                .widthIn(max = maxCardWidth)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF111111))
                    )
                    Text(
                        text = "今日农情",
                        color = Color(0xFF151A16),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 9.dp)
                    )
                    if (dateText != null) {
                        Text(
                            text = dateText,
                            color = Color(0xFF7B7F87),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            textAlign = TextAlign.End
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Color(0xFFE4E6EA),
                            thickness = 0.7.dp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Box(modifier = Modifier.height(10.dp))
                    }
                    TodayAgriNewsItem(
                        item = item,
                        index = index
                    )
                }
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

@Composable
private fun TodayAgriNewsItem(
    item: SessionApi.TodayAgriCardItem,
    index: Int
) {
    val title = item.title.orEmpty().trim()
    val summary = item.summary.orEmpty().trim()
    val source = item.source.orEmpty().trim()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = Color(0xFFF1F2F4),
            shape = CircleShape,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (index + 1).toString(),
                    color = Color(0xFF111111),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF111111),
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = Color(0xFF4F535A),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            if (source.isNotEmpty()) {
                Text(
                    text = "来源：$source",
                    color = Color(0xFF868B91),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun todayAgriDateText(dateCn: String?): String? {
    val raw = dateCn?.trim().orEmpty()
    if (raw.length != 8) return null
    val month = raw.substring(4, 6).trimStart('0').ifBlank { "0" }
    val day = raw.substring(6, 8).trimStart('0').ifBlank { "0" }
    return "${month}月${day}日"
}
