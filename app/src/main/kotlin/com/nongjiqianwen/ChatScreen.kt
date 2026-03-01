package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT, SYSTEM }
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
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
            isStreaming = false
            assistantMessageId = null
            userStopped = false
        }
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty() || isStreaming) return

        val userMsgId = "user_${UUID.randomUUID()}"
        val clientMsgId = "cm_${UUID.randomUUID()}"
        messages.add(ChatMessage(userMsgId, ChatRole.USER, text))
        input.value = ""
        isStreaming = true
        assistantMessageId = null
        userStopped = false

        SessionApi.streamChat(
            options = SessionApi.StreamOptions(
                sessionId = sessionId,
                clientMsgId = clientMsgId,
                text = text,
                images = emptyList()
            ),
            onChunk = { piece -> appendAssistantChunk(piece) },
            onComplete = { finishStreaming() },
            onInterrupted = { reason ->
                mainHandler.post {
                    if (!userStopped && reason != "replay" && reason != "canceled" && assistantMessageId == null) {
                        messages.add(
                            ChatMessage(
                                id = "system_${UUID.randomUUID()}",
                                role = ChatRole.SYSTEM,
                                content = when (reason) {
                                    "quota" -> "今日次数已用完，请在会员中心查看剩余次数。"
                                    "auth" -> "登录失效，请重新进入后重试。"
                                    else -> "网络或服务异常，请稍后重试。"
                                }
                            )
                        )
                    }
                    if (!userStopped && reason != "replay" && reason != "canceled") {
                        showErrorMessage(reason)
                    }
                    finishStreaming()
                }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F6)),
    ) {
        TopAppBar(
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "农技千问",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            state = listState,
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val bubbleColor = when (msg.role) {
                        ChatRole.USER -> Color(0xFFF0F0F2)
                        ChatRole.ASSISTANT -> Color.Transparent
                        ChatRole.SYSTEM -> Color(0xFFF7F7F8)
                    }
                    val align = if (msg.role == ChatRole.USER) Alignment.CenterEnd else Alignment.CenterStart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = align
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bubbleColor)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
            OutlinedTextField(
                value = input.value,
                onValueChange = {
                    if (it.length <= 3000) input.value = it
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("描述作物/地区/问题，我来帮你分析") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
            )

            val canSend = input.value.trim().isNotEmpty()
            Button(
                onClick = {
                    if (isStreaming) {
                        userStopped = true
                        SessionApi.cancelCurrentStream()
                        finishStreaming()
                    } else {
                        sendMessage()
                    }
                },
                enabled = isStreaming || canSend,
                modifier = Modifier.height(44.dp),
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Close else Icons.Default.Send,
                    contentDescription = if (isStreaming) "停止" else "发送"
                )
                Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                Text(if (isStreaming) "停止" else "发送")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}
