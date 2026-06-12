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
    return title == "今日农情" &&
        cardItems.size == 3 &&
        cardItems.all { item ->
            !item.title.isNullOrBlank() &&
                !item.summary.isNullOrBlank()
        }
}

@Composable
fun TodayAgriNewsCard(
    card: SessionApi.TodayAgriCard,
    horizontalPadding: Dp = 20.dp,
    maxCardWidth: Dp = 560.dp,
    modifier: Modifier = Modifier
) {
    val items = card.items.orEmpty().take(3)
    if (items.size != 3) return
    val dateText = todayAgriDateText(card.dateCn)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.8.dp, Color(0xFFDDE3DA)),
            modifier = Modifier
                .widthIn(max = maxCardWidth)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF2F6B3A))
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
                            color = Color(0xFFE8ECE5),
                            thickness = 0.7.dp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Box(modifier = Modifier.height(12.dp))
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
        dateCn = "20260511",
        title = "今日农情",
        items = listOf(
            SessionApi.TodayAgriCardItem(
                title = "华北麦区防干热风",
                summary = "华北多地小麦进入灌浆关键期，气象部门提醒关注高温干风，适时浇水稳粒重。",
                source = "中国天气网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "早稻病虫进入防控期",
                summary = "南方早稻陆续分蘖拔节，植保系统提示加强纹枯病和稻飞虱巡查，抓住窗口防治。",
                source = "全国农技推广网"
            ),
            SessionApi.TodayAgriCardItem(
                title = "蔬菜价格稳中有降",
                summary = "批发市场监测显示多类蔬菜供应增加，部分叶菜价格回落，种植户可关注本地走货节奏。",
                source = "农业农村部"
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
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = Color(0xFFEAF1E6),
            shape = CircleShape,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (index + 1).toString(),
                    color = Color(0xFF315D34),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = Color(0xFF4F535A),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
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
