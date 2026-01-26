package com.nongjiqianwen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nongjiqianwen.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

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
            
            if (normalizedInput != null) {
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
                    ModelService.getReply(normalizedInput) { chunk ->
                        replyText += chunk
                        // 更新最后一条消息
                        adapter.updateLastMessage(replyText)
                        // 自动滚动到底部
                        binding.chatList.post {
                            binding.chatList.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }, 300)
            }
        }
    }
}
