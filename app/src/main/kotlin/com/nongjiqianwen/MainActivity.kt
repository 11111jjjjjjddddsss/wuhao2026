package com.nongjiqianwen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nongjiqianwen.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var isRequesting = false
    
    // 用于延迟重置状态的 Runnable
    private val resetStateRunnable = Runnable {
        resetRequestState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        
        adapter = MessageAdapter(messages)
        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter
        
        binding.send.setOnClickListener {
            val rawInput = binding.input.text.toString()
            val normalizedInput = InputNormalizer.normalize(rawInput)
            
            if (normalizedInput != null && !isRequesting) {
                // 设置请求状态，防止重复请求
                isRequesting = true
                binding.send.isEnabled = false
                
                try {
                    // 添加用户消息
                    adapter.addMessage(Message(normalizedInput, true))
                    binding.input.text.clear()
                    
                    // 自动滚动到底部
                    binding.chatList.post {
                        binding.chatList.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                    
                    // 流式返回固定假回复
                    binding.chatList.postDelayed({
                        // 先创建一条空消息
                        adapter.addMessage(Message("", false))
                        var replyText = ""
                        
                        ModelService.getReply(normalizedInput, 
                            onChunk = { chunk ->
                                replyText += chunk
                                
                                // 更新最后一条消息
                                adapter.updateLastMessage(replyText)
                                // 自动滚动到底部
                                binding.chatList.post {
                                    binding.chatList.smoothScrollToPosition(adapter.itemCount - 1)
                                }
                            },
                            onComplete = {
                                // 请求完成，重置状态
                                resetRequestState()
                            }
                        )
                        
                        // 备用超时：如果 60 秒内没有完成回调，强制重置状态
                        binding.chatList.postDelayed(resetStateRunnable, 60000)
                    }, 300)
                } catch (e: Exception) {
                    // 确保在异常情况下也重置状态
                    resetRequestState()
                } finally {
                    // 确保无论成功失败都重置状态（作为最后保障）
                    // 注意：由于异步请求，这里可能过早重置，主要依赖超时机制
                }
            }
        }
    }
    
    /**
     * 重置请求状态（在 finally 中调用，确保无论成功失败都重置）
     */
    private fun resetRequestState() {
        isRequesting = false
        binding.send.isEnabled = true
    }
    }
}
