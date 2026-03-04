package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT }
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val index: String, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val numberedRegex = Regex("""^(\d+)[.)]\s+(.*)$""")

private fun parseMarkdownLikeBlocks(content: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val lines = content.replace("\r\n", "\n").split('\n')
    var inCode = false
    val code = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trim())
            paragraph.clear()
        }
    }

    lines.forEach { raw ->
        val trimmed = raw.trim()

        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.joinToString("\n"))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code += raw
            return@forEach
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val headingLevel = when {
            trimmed.startsWith("### ") -> 3
            trimmed.startsWith("## ") -> 2
            trimmed.startsWith("# ") -> 1
            else -> 0
        }
        if (headingLevel > 0) {
            flushParagraph()
            blocks += MdBlock.Heading(headingLevel, trimmed.substring(headingLevel + 1).trim())
            return@forEach
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            flushParagraph()
            blocks += MdBlock.Bullet(trimmed.substring(2).trim())
            return@forEach
        }

        val numbered = numberedRegex.matchEntire(trimmed)
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2].trim())
            return@forEach
        }

        paragraph += trimmed
    }

    flushParagraph()
    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
    return blocks
}

@Composable
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { parseMarkdownLikeBlocks(content) }
    val paragraphStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 30.sp,
        color = Color(0xFF181818)
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 22.sp
                        2 -> 20.sp
                        else -> 18.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = size,
                        lineHeight = (size.value + 8).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF141414)
                    )
                }

                is MdBlock.Paragraph -> Text(text = block.text, style = paragraphStyle)

                is MdBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(text = "•", fontSize = 17.sp, lineHeight = 30.sp, color = Color(0xFF181818))
                        Text(text = block.text, style = paragraphStyle, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.Numbered -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(text = "${block.index}.", fontSize = 17.sp, lineHeight = 30.sp, color = Color(0xFF181818))
                        Text(text = block.text, style = paragraphStyle, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.Code -> {
                    Text(
                        text = block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        color = Color(0xFF1C1C1C)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBreathingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistantBreathingDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotAlpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotScale"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color(0xFF111111))
    )
}

@Composable
fun ChatScreen() {
    val input = remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }

    var isStreaming by remember { mutableStateOf(false) }
    var assistantMessageId by remember { mutableStateOf<String?>(null) }
    var shouldStickToBottom by remember { mutableStateOf(true) }
    var userStopped by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun appendAssistantChunk(piece: String) {
        if (piece.isBlank()) return
        mainHandler.post {
            val currentId = assistantMessageId
            if (currentId.isNullOrBlank()) {
                val newId = "assistant_${UUID.randomUUID()}"
                assistantMessageId = newId
                messages.add(ChatMessage(newId, ChatRole.ASSISTANT, piece))
                return@post
            }
            val index = messages.indexOfLast { it.id == currentId }
            if (index >= 0) {
                val old = messages[index]
                messages[index] = old.copy(content = old.content + piece)
            } else {
                messages.add(ChatMessage(currentId, ChatRole.ASSISTANT, piece))
            }
        }
    }

    fun finishStreaming() {
        mainHandler.post {
            val currentId = assistantMessageId
            if (!currentId.isNullOrBlank()) {
                val index = messages.indexOfLast { it.id == currentId }
                if (index >= 0 && messages[index].role == ChatRole.ASSISTANT && messages[index].content.isBlank()) {
                    messages.removeAt(index)
                }
            }
            isStreaming = false
            assistantMessageId = null
            userStopped = false
        }
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        val userId = "user_${UUID.randomUUID()}"
        messages.add(ChatMessage(userId, ChatRole.USER, text))
        input.value = ""

        val assistantId = "assistant_${UUID.randomUUID()}"
        messages.add(ChatMessage(assistantId, ChatRole.ASSISTANT, ""))
        isStreaming = true
        userStopped = false
        assistantMessageId = assistantId

        fakeStreamJob?.cancel()
        val fullText = FAKE_STREAM_TEXT
        fakeStreamJob = snackbarScope.launch {
            var cursor = 0
            var emitted = 0
            while (isActive && cursor < fullText.length) {
                val chunkSize = Random.nextInt(6, 21)
                val next = min(cursor + chunkSize, fullText.length)
                val piece = fullText.substring(cursor, next)
                appendAssistantChunk(piece)
                emitted += piece.length
                cursor = next
                delay(Random.nextLong(20, 41))
                if (emitted >= 140 && cursor < fullText.length) {
                    emitted = 0
                    delay(Random.nextLong(220, 421))
                }
            }
            if (isActive) finishStreaming()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && shouldStickToBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val total = info.totalItemsCount
                if (total == 0) return@map true
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
            .collectLatest { shouldStickToBottom = it }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        containerColor = Color(0xFFF5F5F5),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = {},
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF252525))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            modifier = Modifier.size(30.dp),
                            tint = Color(0xFF252525)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        TextField(
                            value = input.value,
                            onValueChange = {
                                if (it.length <= 3000) input.value = it
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterStart)
                                .padding(end = 52.dp),
                            placeholder = { Text("描述作物/地区/问题", color = Color(0xFF9A9A9A)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )

                        val canSend = input.value.trim().isNotEmpty()
                        val actionEnabled = isStreaming || canSend
                        val actionBg = if (actionEnabled) Color(0xFF101010) else Color.Transparent
                        val actionTint = if (actionEnabled) Color.White else Color(0xFF1A1A1A)

                        IconButton(
                            onClick = {
                                if (isStreaming) {
                                    userStopped = true
                                    fakeStreamJob?.cancel()
                                    finishStreaming()
                                } else if (canSend) {
                                    sendMessage()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(actionBg)
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Default.Close else Icons.Default.Send,
                                contentDescription = if (isStreaming) "停止" else "发送",
                                tint = actionTint
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                state = listState,
                contentPadding = PaddingValues(
                    top = if (messages.isEmpty()) 80.dp else 120.dp,
                    bottom = 8.dp
                )
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 132.dp, end = 4.dp)
                        ) {
                            Text(
                                text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF141414),
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val align = if (msg.role == ChatRole.USER) Alignment.CenterEnd else Alignment.CenterStart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = align
                        ) {
                            if (msg.role == ChatRole.ASSISTANT) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .padding(top = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (isStreaming && msg.id == assistantMessageId) {
                                            StreamingBreathingDot()
                                        }
                                    }
                                    AssistantMarkdownContent(
                                        content = msg.content,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Text(
                                    text = msg.content,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFECECEF))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF161616)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单", tint = Color(0xFF222222))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = "农技千问",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color(0xFF111111),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private val FAKE_STREAM_TEXT = """
### 农业问诊演示长文（本地假流式）
这是一段用于前端联调和排版验收的示例回复，目的是模拟真实问诊场景下的连续输出体验。以下内容不连接后端、不触发检索、不调用模型，仅用于观察聊天界面的可读性、滚动稳定性、段落节奏和逐段追加的视觉反馈。你可以把它看成一次完整的农业问诊答复模板：先做范围收敛，再给判断路径，最后给观察与复核建议。全篇避免给出任何具体药方定量，重点是流程和方法，确保展示时既像真实对话，又不会被误用为直接处置方案。

## 一、先把问题描述收敛清楚
在农业问诊里，最怕信息多但关键条件缺失。如果没有把作物、阶段、环境和变化节奏描述清楚，再多经验也只能停留在猜测层面。建议先按下面的结构整理信息：

1. 作物与生育阶段：是苗期、营养生长期、开花坐果期，还是成熟采收前。
2. 异常起点：最早从哪个部位出现变化，是新叶、老叶、茎部还是根际表现先异常。
3. 变化速度：两天内快速扩展，还是一周内缓慢累积。
4. 分布范围：零散点状、片区集中，还是整棚、整田普遍出现。
5. 管理记录：近期是否有浇水节奏变化、棚内通风变化、天气突变或机械扰动。

这些信息的价值在于：它能把病害、虫害、生理性失衡、环境胁迫四类方向先分开，而不是一开始就陷入细枝末节。

## 二、先判断风险级别，再决定回应节奏
问诊不只是回答是什么，更要回答现在该不该马上做动作。如果风险级别判断错误，要么耽误窗口期，要么过度处置。建议按三层分级：

- 低风险：症状轻、范围小、变化慢，可先观察记录，再做低干预调整。
- 中风险：范围在扩大但尚可控，需要建立日观察点并同步管理微调。
- 高风险：短时扩展明显、关联条件恶化，需要立即组织复核并优先做可逆措施。

注意：高风险不等于立刻重处置。真正稳健的做法是先止损、再确认、后升级，避免在证据不足时一步走到不可逆动作。

## 三、把回答写成可执行结构
为了让一线用户看完就能行动，建议固定三段式：

1. 结论一句话：当前更像哪一类问题，暂不支持哪一类。
2. 依据两到四条：只写看得见、可复核的事实，不写玄学判断。
3. 下一步两件事：一个是当日可做，一个是次日复核，形成闭环。

示例表达方式：
目前更像环境胁迫叠加管理波动，暂不支持单一病害爆发。依据是异常先出现在边缘区、症状随时段波动明显、同地块差异与通风条件相关。下一步先做低风险稳定处理，并在次日同一时段复查新叶状态与扩展边界。

## 四、如何观察图片与现场描述
图片常见问题是拍得多但证据弱。建议引导用户补充以下要点：

- 近景：异常组织的纹理、边界、色泽过渡。
- 中景：整株上下部位差异，不同叶位对比。
- 远景：同区域内健康株与异常株对照。
- 时间轴：同一位置隔天复拍，验证变化方向。

当证据来自图片时，回答顺序最好是先客观描述，再解释可能机制，最后给验证动作。这样可以显著降低先入为主导致的误判。

## 五、常见误区提醒
下面这些误区在移动端咨询里非常常见，会直接影响判断质量：

- 误区一：把结果当原因。看到黄化就直接归因某单一因素，忽略多因叠加。
- 误区二：忽略时间维度。只看一张图，不看连续变化，容易把暂态波动当趋势。
- 误区三：过早下最终结论。证据不足时应给条件化判断，并说明复核节点。
- 误区四：动作顺序颠倒。应先可逆、低风险，再考虑高成本或不可逆动作。

## 六、移动端阅读友好的表达规则
长文不是问题，难读才是问题。为了保证在手机上连续阅读不卡顿，建议：

1. 每段表达一个中心，不要把多个结论塞进同一长句。
2. 关键句前置，解释放后面，先让用户知道现在做什么。
3. 条目密度适中，连续列表后要有过渡句，避免视觉疲劳。
4. 对不确定结论必须标注条件，减少误解和反复追问。

这套规则对任何屏幕尺寸都有效，因为它降低的是认知负担，而不是单纯依赖字号或留白。

## 七、现场执行与复盘建议
问诊价值最终体现在能否落地。建议每次回答都附带一个轻量复盘框架：

- 今日目标：先稳住扩展，确认是否继续恶化。
- 观察点位：固定三到五个点，保持同角度、同时间记录。
- 复核时间：次日同一时段进行对比，避免时段偏差干扰判断。
- 升级条件：出现哪些信号才进入更高等级处置。

这样做的好处是：即使首次判断不是最优，也能通过连续证据快速修正，而不是在模糊状态下反复摇摆。

## 八、演示结语
以上内容仅用于 UI 假流式演示，重点是观察以下体验是否稳定：
一是呼吸指示点是否在流式阶段持续出现；二是正文是否按同一条消息持续追加；三是滚动是否平滑且不会跳动；四是结束时状态是否正确收口。
如果这些都稳定，后续切回真实 SSE 时，用户侧感知会基本一致，排版和交互风险也会明显降低。
""".trimIndent()
