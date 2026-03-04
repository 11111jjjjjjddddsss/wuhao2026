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
import kotlinx.coroutines.launch
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
    val sessionId = remember { IdManager.getSessionId() }

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

    fun showErrorMessage(reason: String) {
        val message = when (reason) {
            "auth" -> "登录失效，请重新进入后重试"
            "quota" -> "今日次数已用完，请在会员中心查看剩余次数"
            "network", "server", "timeout" -> "网络或服务异常，请稍后重试"
            else -> "请求中断，请重试"
        }
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
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

        val options = SessionApi.StreamOptions(
            sessionId = sessionId,
            clientMsgId = UUID.randomUUID().toString(),
            text = text,
            images = emptyList()
        )

        SessionApi.streamChat(
            options = options,
            onChunk = { piece -> appendAssistantChunk(piece) },
            onComplete = { finishStreaming() },
            onInterrupted = { reason ->
                finishStreaming()
                if (userStopped && (reason == "canceled" || reason == "interrupted")) return@streamChat
                showErrorMessage(reason)
            }
        )
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
                                    SessionApi.cancelCurrentStream()
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

private val MARKDOWN_DEMO_TEXT = """
### 本地渲染演示
下面这段是用于排版验收的示例文本，目的是观察长文滚动、标题层级、列表密度和连续阅读体验在移动端是否稳定。它不会参与线上业务，只用于 UI 联调。你可以把它当成“流式回答完成后的最终排版样本”，重点看每段行高是否均匀、段落间是否有呼吸感、列表是否不会挤成一团、长句换行是否顺滑。

在农业问诊场景里，真正有价值的回答不是堆知识点，而是先缩小判断范围，再给可执行动作。建议采用三段结构：先结论、再依据、最后下一步。这样用户读完可以立刻行动，而不是继续反复追问。尤其是移动端屏幕小，用户通常是在田间、路上、仓库等场景快速查看内容，如果首段不能迅速给出方向，后续再详细也会被跳过。因此，渲染层要先保证“读得下去”，再谈“信息很多”。

## 一、先看信息结构
1. 作物与阶段：作物种类、栽培方式、当前生育阶段。
2. 变化特征：是突发还是渐变，是局部还是成片。
3. 环境条件：近三天温湿度、灌溉节奏、近期施肥和用药。
4. 关键证据：图片里最早异常的部位和颜色变化。

- 如果新叶先异常，优先考虑吸收和微量元素问题。
- 如果老叶先异常，优先排查基础营养和根区环境。
- 如果有扩展斑点，优先排查病原性风险。

## 二、把结论写成可执行信息
建议把回答写成“结论一句话 + 依据两三条 + 下一步两步法”。这样不仅能降低误解，还能显著减少用户反复追问“那我现在具体该干什么”。在你当前这个产品阶段，最应该优先优化的是“结构一致性”，因为只要结构稳定，后续换模型、换后端、加联网，都不需要大改前端排版。

结论示例：当前更像吸收障碍，不像单一病害暴发。依据示例：新叶失绿明显，叶片边缘无典型病斑扩展，且近期连续阴雨导致根区含氧下降。下一步示例：先做低风险稳根处理，24小时后复看新叶状态，再决定是否上更强干预。这种写法的好处在于，用户可以先做第一步，不需要一次性理解全部机理。

## 三、长文可读性检查
请重点观察四个点：第一，行高是否稳定不压迫；第二，段间距是否统一；第三，滚动时是否跳动；第四，键盘弹起后输入栏和正文关系是否自然。如果其中任意一项表现不稳，用户会主观觉得“回答质量差”，哪怕文本本身是对的。这是典型的人机体验问题，不是农业知识问题。

为了压力演示，再给一段连续正文：在连续追问场景中，系统需要保持判断连续性，不能每轮都从零开始。理想状态是，每一轮都能承接上一轮确定的信息，同时对新增证据做增量修正。这样既能控制答复长度，也能稳定结论方向，避免一会儿像营养缺失、一会儿又跳到病害暴发。若用户补充了环境变化，例如突遇降温、持续阴雨、灌溉频率变化，应该优先把这些高影响因素放在判断前面，而不是继续沿用旧结论。对于高风险动作，比如清园、重度用药、拔除整株等，回答要给出不确定性和验证路径，避免一次性把结论说死。对于图片证据，建议先描述客观可见现象，再进入归因，减少先入为主的偏差。最后，给出的动作建议应当有顺序：先低风险、可逆操作，再高风险、不可逆操作，这样用户执行成本更低，也更容易回看和复盘。

另外从渲染角度看，正文字号过小会让用户频繁放大，字号过大又会导致一屏信息过少。你当前采用的 17sp 正文字号和 30sp 行高，在中等尺寸手机上属于“偏舒适阅读”的区间，适合长回答。段间距如果过窄，用户会把两段误读成一段；过宽又会产生断裂感。你现在使用 10dp 的段间距，实际观感接近 GPT 移动端，属于可继续沿用的参数。

在交互层面，最关键的是“新增消息时自动滚到底，但用户手动上滑时不抢滚动”。这个规则在聊天产品里是硬需求，因为用户常常需要回看上一段操作建议。你当前实现里已经按“接近底部才自动跟随”的思路处理，这在体验上是正确方向。后续若再优化，可以加一个“回到底部”小按钮，仅在用户离底部较远且有新内容时显示。

再补充一段用于观察列表稳定性：当用户连续发送短句、再发送长段、再插入图片说明时，列表高度会快速变化，若 item key 不稳定或重组逻辑有误，就会出现跳动、错位、重复渲染。现在你用消息 id 做 key，这能避免大部分重组抖动。后续如果加入“流式增量段落”，记得只更新最后一条 assistant 消息，不要每个 chunk 新建一条，否则性能和观感都会明显下降。

最后作为演示收尾，这段文字本身没有业务意义，仅用于检查排版和滚动稳定性：标题、段落、列表、连续长文、不同长度句子混排、中文标点密度、数字与单位混排（如 24 小时、6–9 轮、3000 字）在同一页面中的显示是否一致。只要这些基础渲染稳定，后面接入真实后端流式输出时，你就不会再因为 UI 层“看起来像故障”而误判模型质量。
""".trimIndent()
