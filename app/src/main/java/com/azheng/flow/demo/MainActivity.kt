package com.azheng.flow.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.azheng.event.flow.*
import com.azheng.flow.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    // 保存 receiveEventHandler 返回的 Job，用于手动取消
    private var handlerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSendButtons()
        setupReceiveEvents()
    }

    /**
     * 设置发送按钮点击事件
     */
    private fun setupSendButtons() {
        // ==================== 发送事件示例 ====================

        // 1. 生命周期感知的事件发送（推荐）
        binding.btnSendEvent.setOnClickListener {
            sendEvent(LoginEvent("user_001", "张三"))
            showToast("已发送 LoginEvent")
        }

        // 2. 带标签的事件发送
        binding.btnSendEventWithTag.setOnClickListener {
            sendEvent(
                event = MessageEvent("这是一条VIP消息", type = 1),
                tag = "vip_channel"
            )
            showToast("已发送带标签的 MessageEvent")
        }

        // 3. 使用 LifecycleOwner 扩展函数发送（绑定生命周期）
        binding.btnSendEventLifecycle.setOnClickListener {
            this.sendEvent(
                event = RefreshEvent("user_data", forceRefresh = true),
                tag = "refresh"
            )
            showToast("已发送 RefreshEvent（生命周期感知）")
        }

        // 4. 在协程中使用挂起函数发送
        binding.btnSendEventSuspend.setOnClickListener {
            lifecycleScope.launch {
                sendEventSuspend(MessageEvent("挂起函数发送的消息"))
                showToast("已通过挂起函数发送事件")
            }
        }

        // 5. 批量发送事件（模拟后台连续发送）
        binding.btnSendMultipleEvents.setOnClickListener {
            lifecycleScope.launch {
                repeat(5) { index ->
                    sendEventSuspend(MessageEvent("批量消息 #${index + 1}"))
                    delay(100)
                }
                showToast("已批量发送 5 条消息")
            }
        }

        // ==================== 发送标签示例 ====================

        // 6. 发送纯标签事件
        binding.btnSendTag.setOnClickListener {
            sendTag("action_refresh")
            showToast("已发送标签: action_refresh")
        }

        // 7. 使用 LifecycleOwner 扩展函数发送标签
        binding.btnSendTagLifecycle.setOnClickListener {
            this.sendTag("action_logout")
            showToast("已发送标签: action_logout")
        }

        // 8. 在协程中使用挂起函数发送标签
        binding.btnSendTagSuspend.setOnClickListener {
            lifecycleScope.launch {
                sendTagSuspend("action_sync")
                showToast("已通过挂起函数发送标签")
            }
        }

        // 9. 跳转到 SecondActivity
        binding.btnGoToSecond.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
    }

    /**
     * 设置接收事件
     */
    private fun setupReceiveEvents() {
        // ==================== 接收事件示例 ====================

        // 1. 基础事件接收（生命周期感知）
        receiveEvent<LoginEvent> { event ->
            Log.d(TAG, "收到 LoginEvent: userId=${event.userId}, userName=${event.userName}")
            updateLog("收到登录事件: ${event.userName}")
        }

        // 2. 带标签过滤的事件接收
        receiveEvent<MessageEvent>("vip_channel") { event ->
            Log.d(TAG, "收到 VIP MessageEvent: ${event.content}")
            updateLog("收到VIP消息: ${event.content}")
        }

        // 3. 多标签过滤的事件接收（匹配任意一个标签即可）
        receiveEvent<MessageEvent>("channel_a", "channel_b", "channel_c") { event ->
            Log.d(TAG, "收到多标签 MessageEvent: ${event.content}")
            updateLog("收到多标签消息: ${event.content}")
        }

        // 4. 使用 receiveEventLive - 延迟到前台接收（接收所有事件）
        receiveEventLive<RefreshEvent>(
            "refresh",
            onlyReceiveLatest = false  // 接收所有事件，不会丢失
        ) { event ->
            Log.d(TAG, "前台收到 RefreshEvent: ${event.dataType}")
            updateLog("前台收到刷新事件: ${event.dataType}")
        }

        // 5. 使用 receiveEventLive - 只接收最新事件
        receiveEventLive<MessageEvent>(
            onlyReceiveLatest = true  // 回到前台只处理最后一个事件
        ) { event ->
            Log.d(TAG, "前台收到最新 MessageEvent: ${event.content}")
            updateLog("前台收到最新消息: ${event.content}")
        }

        // ==================== 接收标签示例 ====================

        // 6. 基础标签接收
        receiveTag("action_refresh", "action_sync") { tag ->
            Log.d(TAG, "收到标签: $tag")
            updateLog("收到标签: $tag")
        }

        // 7. 使用 receiveTagLive - 延迟到前台接收标签
        receiveTagLive(
            "action_logout",
            onlyReceiveLatest = false
        ) { tag ->
            Log.d(TAG, "前台收到标签: $tag")
            updateLog("前台收到标签: $tag")
        }

        // 8. 不绑定生命周期的事件接收（需要手动取消！）
        handlerJob = receiveEventHandler<MessageEvent> { event ->
            Log.d(TAG, "Handler 收到 MessageEvent: ${event.content}")
        }
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            val currentText = binding.tvLog.text.toString()
            binding.tvLog.text = "$currentText\n$message"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 重要：手动取消不绑定生命周期的 Job，避免内存泄漏
        handlerJob?.cancel()
    }
}
