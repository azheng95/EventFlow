package com.azheng.flow.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.azheng.event.flow.*
import com.azheng.flow.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    // 保存 receiveEventHandler 返回的 Job，用于手动取消
    private var handlerJob: Job? = null

    // 保存独立 Bus 的 receiveEventHandler Job，用于手动取消
    private var chatHandlerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSendButtons()
        setupReceiveEvents()
    }
    private val results = mutableListOf<Int>()

    /**
     * 设置发送按钮点击事件
     */
    private fun setupSendButtons() {
        // ==================== 发送事件示例 ====================
        binding.btnBusStress.setOnClickListener {
            lifecycleScope.launch {
                FlowEventBusStressTest().runAllTests()
            }
        }
        binding.btnMutex.setOnClickListener {
            // 快速发送3个事件
            lifecycleScope.launch {
                sendEventSuspend(1)
                sendEventSuspend(2)
                sendEventSuspend(3)
            }

        }
        // 订阅事件
        receiveEvent<Int> { number ->
            // 模拟不同的处理时间
            delay((4 - number) * 100L)  // 数字越小，处理越慢
            results.add(number)
            Log.d("FlowEventBusStressTest", "处理完成: $number, 当前结果: $results")
        }




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

        // ==================== 独立Bus发送示例 ====================

        // 9. 发送聊天消息到 chatBus（仅 chatBus 的订阅者能收到）
        binding.btnSendChatBus.setOnClickListener {
            sendEvent(
                event = ChatEvent("张三", "你好，这是 chatBus 消息"),
                bus = AppBus.chatBus
            )
            showToast("已发送 ChatEvent 到 chatBus")
        }

        // 10. 发送同类型 ChatEvent 到全局 Bus（验证隔离：chatBus 订阅者收不到）
        binding.btnSendChatGlobal.setOnClickListener {
            sendEvent(ChatEvent("系统", "这是全局 Bus 的 ChatEvent"))
            showToast("已发送 ChatEvent 到全局 Bus")
        }

        // 11. 发送行情到 pricingBus
        binding.btnSendPriceBus.setOnClickListener {
            lifecycleScope.launch {
                // 模拟快速行情推送，因为 pricingBus 用 DROP_OLDEST，旧的会被丢弃
                repeat(10) { index ->
                    val price = 100.0 + index * 0.5
                    sendEventSuspend(
                        event = PriceEvent("AAPL", price),
                        bus = AppBus.pricingBus
                    )
                    delay(50)
                }
                showToast("已发送 10 条行情到 pricingBus")
            }
        }

        // 12. 发送标签到 chatBus（验证标签也支持独立 Bus）
        binding.btnSendTagChatBus.setOnClickListener {
            sendTag("chat_typing", bus = AppBus.chatBus)
            showToast("已发送标签 chat_typing 到 chatBus")
        }

        // 13. 独立 Bus 隔离对比测试：同时向三个 Bus 发送，验证互不干扰
        binding.btnIsolationTest.setOnClickListener {
            lifecycleScope.launch {
                // 同时向全局、chatBus、pricingBus 发送不同内容的 MessageEvent
                sendEventSuspend(MessageEvent("全局Bus的消息"))
                sendEventSuspend(
                    event = MessageEvent("chatBus的消息"),
                    bus = AppBus.chatBus
                )
                sendEventSuspend(
                    event = MessageEvent("pricingBus的消息"),
                    bus = AppBus.pricingBus
                )
                showToast("已向三个Bus各发送一条 MessageEvent，查看日志验证隔离")
            }
        }

        // 14. 跳转到 SecondActivity
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

        // ==================== 独立Bus接收示例 ====================

        // 9. 接收 chatBus 上的 ChatEvent（全局 Bus 的 ChatEvent 不会触发此回调）
        receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到 ChatEvent: from=${event.from}, msg=${event.message}")
            updateLog("【chatBus】聊天: ${event.from} 说: ${event.message}")
        }

        // 10. 接收全局 Bus 上的 ChatEvent（chatBus 的 ChatEvent 不会触发此回调）
        receiveEvent<ChatEvent> { event ->
            Log.d(TAG, "【全局Bus】收到 ChatEvent: from=${event.from}, msg=${event.message}")
            updateLog("【全局Bus】聊天: ${event.from} 说: ${event.message}")
        }

        // 11. 接收 pricingBus 上的 PriceEvent（使用 receiveEventLive，只关心最新行情）
        receiveEventLive<PriceEvent>(
            onlyReceiveLatest = true,  // 只接收最新行情
            bus = AppBus.pricingBus
        ) { event ->
            Log.d(TAG, "【pricingBus】最新行情: ${event.symbol} = ${event.price}")
            updateLog("【pricingBus】最新行情: ${event.symbol} = $${event.price}")
        }

        // 12. 接收 chatBus 上的标签事件
        receiveTag("chat_typing", bus = AppBus.chatBus) { tag ->
            Log.d(TAG, "【chatBus】收到标签: $tag")
            updateLog("【chatBus】标签: $tag")
        }

        // 13. 隔离验证：在全局 Bus 上接收 MessageEvent（chatBus/pricingBus 的 MessageEvent 不会出现在这里）
        receiveEvent<MessageEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到 MessageEvent: ${event.content}")
            updateLog("【chatBus隔离验证】消息: ${event.content}")
        }

        receiveEvent<MessageEvent>(bus = AppBus.pricingBus) { event ->
            Log.d(TAG, "【pricingBus】收到 MessageEvent: ${event.content}")
            updateLog("【pricingBus隔离验证】消息: ${event.content}")
        }

        // 14. 不绑定生命周期的独立 Bus 事件接收（需要手动取消！）
        chatHandlerJob = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus Handler】收到: ${event.from} - ${event.message}")
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
        chatHandlerJob?.cancel()
    }
}
