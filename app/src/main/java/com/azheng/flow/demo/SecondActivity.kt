package com.azheng.flow.demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.azheng.event.flow.*
import com.azheng.flow.demo.databinding.ActivitySecondBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SecondActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecondBinding
    private val TAG = "SecondActivity"
    private var handlerJob: Job? = null
    private var chatHandlerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupReceiveEvents()
        setupSendButtons()
    }

    /**
     * 设置接收事件 - 展示各种接收方式
     */
    private fun setupReceiveEvents() {
        // ==================== 基础接收 ====================

        // 1. 接收所有 LoginEvent（不过滤标签）
        receiveEvent<LoginEvent> { event ->
            Log.d(TAG, "收到 LoginEvent: ${event.userName}")
            updateLog("登录事件: ${event.userName} (${event.userId})")
        }

        // 2. 接收所有 MessageEvent（不过滤标签）
        receiveEvent<MessageEvent> { event ->
            Log.d(TAG, "收到 MessageEvent: ${event.content}")
            updateLog("消息事件: ${event.content}")
        }

        // 3. 只接收特定标签的 MessageEvent
        receiveEvent<MessageEvent>("vip_channel") { event ->
            updateLog("【VIP】${event.content}")
        }

        // ==================== LiveData 模式接收 ====================

        // 4. receiveEventLive - 后台事件延迟到前台处理
        // 场景：App 在后台时收到多条消息，回到前台时统一处理
        receiveEventLive<RefreshEvent>(
            onlyReceiveLatest = false  // false: 处理所有事件
        ) { event ->
            Log.d(TAG, "LiveData收到 RefreshEvent: ${event.dataType}")
            updateLog("【LiveData】刷新: ${event.dataType}")
        }

        // 5. receiveEventLive - 只处理最新事件
        // 场景：用户位置更新，只关心最新位置
        receiveEventLive<MessageEvent>(
            "location_update",
            onlyReceiveLatest = true  // true: 只处理最后一个
        ) { event ->
            updateLog("【最新位置】${event.content}")
        }

        // ==================== 标签接收 ====================

        // 6. 接收指定标签
        receiveTag("action_refresh", "action_sync", "action_logout") { tag ->
            Log.d(TAG, "收到标签: $tag")
            updateLog("标签事件: $tag")

            when (tag) {
                "action_refresh" -> performRefresh()
                "action_sync" -> performSync()
                "action_logout" -> performLogout()
            }
        }

        // 7. receiveTagLive - 后台标签延迟到前台处理
        receiveTagLive(
            "background_task_complete",
            onlyReceiveLatest = false
        ) { tag ->
            updateLog("【LiveData标签】$tag")
        }

        // ==================== 自定义生命周期事件 ====================

        // 8. 指定在 ON_STOP 时停止接收（而不是默认的 ON_DESTROY）
        receiveEvent<MessageEvent>(
            lifeEvent = Lifecycle.Event.ON_STOP
        ) { event ->
            Log.d(TAG, "ON_STOP前有效: ${event.content}")
        }

        // ==================== 不绑定生命周期的接收 ====================

        // 9. receiveEventHandler - 需要手动取消！
        handlerJob = receiveEventHandler<LoginEvent> { event ->
            Log.d(TAG, "Handler收到: ${event.userName}")
        }

        // ==================== 独立Bus接收示例 ====================

        // 10. 接收 chatBus 上的 ChatEvent
        receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到: ${event.from} - ${event.message}")
            updateLog("【chatBus】${event.from}: ${event.message}")
        }

        // 11. 接收全局 Bus 上的 ChatEvent（验证隔离）
        receiveEvent<ChatEvent> { event ->
            Log.d(TAG, "【全局Bus】收到 ChatEvent: ${event.from} - ${event.message}")
            updateLog("【全局Bus-Chat】${event.from}: ${event.message}")
        }

        // 12. 接收 pricingBus 上的 PriceEvent
        receiveEvent<PriceEvent>(bus = AppBus.pricingBus) { event ->
            Log.d(TAG, "【pricingBus】行情: ${event.symbol} = ${event.price}")
            updateLog("【pricingBus】${event.symbol}: $${event.price}")
        }

        // 13. 使用 receiveEventLive 接收 chatBus 上的消息（后台延迟到前台）
        receiveEventLive<ChatEvent>(
            onlyReceiveLatest = false,
            bus = AppBus.chatBus
        ) { event ->
            Log.d(TAG, "【chatBus-LiveData】收到: ${event.from} - ${event.message}")
            updateLog("【chatBus-Live】${event.from}: ${event.message}")
        }

        // 14. 接收 chatBus 上的标签
        receiveTag("chat_typing", "chat_online", bus = AppBus.chatBus) { tag ->
            Log.d(TAG, "【chatBus】标签: $tag")
            updateLog("【chatBus标签】$tag")
        }

        // 15. receiveTagLive 接收 chatBus 上的标签（后台延迟到前台）
        receiveTagLive(
            "chat_new_member",
            onlyReceiveLatest = false,
            bus = AppBus.chatBus
        ) { tag ->
            updateLog("【chatBus-TagLive】$tag")
        }

        // 16. 不绑定生命周期的独立 Bus 事件接收（需要手动取消！）
        chatHandlerJob = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus Handler】收到: ${event.from} - ${event.message}")
        }
    }

    /**
     * 设置发送按钮 - 从 SecondActivity 发送事件回 MainActivity
     */
    private fun setupSendButtons() {
        // 发送登录成功事件
        binding.btnSendLoginSuccess.setOnClickListener {
            sendEvent(LoginEvent("user_002", "李四"))
            showToast("已发送登录成功事件")
        }

        // 发送普通消息
        binding.btnSendMessage.setOnClickListener {
            sendEvent(MessageEvent("来自 SecondActivity 的消息"))
            showToast("已发送消息")
        }

        // 发送 VIP 消息（带标签）
        binding.btnSendVipMessage.setOnClickListener {
            sendEvent(
                event = MessageEvent("VIP 专属消息", type = 1),
                tag = "vip_channel"
            )
            showToast("已发送 VIP 消息")
        }

        // 发送刷新标签
        binding.btnSendRefreshTag.setOnClickListener {
            sendTag("action_refresh")
            showToast("已发送刷新标签")
        }

        // 批量发送位置更新（测试 onlyReceiveLatest）
        binding.btnSendLocationUpdates.setOnClickListener {
            lifecycleScope.launch {
                repeat(10) { index ->
                    sendEventSuspend(
                        MessageEvent("位置更新 #${index + 1}"),
                        "location_update"
                    )
                    delay(50)
                }
                showToast("已发送 10 次位置更新")
            }
        }

        // 模拟后台任务完成
        binding.btnBackgroundTaskComplete.setOnClickListener {
            lifecycleScope.launch {
                repeat(3) { index ->
                    delay(100)
                    sendTagSuspend("background_task_complete")
                }
                showToast("已发送 3 次后台任务完成标签")
            }
        }

        // ==================== 独立Bus发送示例 ====================

        // 发送聊天消息到 chatBus
        binding.btnSendChatBus.setOnClickListener {
            sendEvent(
                event = ChatEvent("李四", "来自 SecondActivity 的 chatBus 消息"),
                bus = AppBus.chatBus
            )
            showToast("已发送 ChatEvent 到 chatBus")
        }

        // 发送行情到 pricingBus（使用 LifecycleOwner 扩展函数）
        binding.btnSendPriceBus.setOnClickListener {
            this.sendEvent(
                event = PriceEvent("GOOG", 175.50),
                bus = AppBus.pricingBus
            )
            showToast("已发送行情到 pricingBus")
        }

        // 使用挂起函数发送到 chatBus
        binding.btnSendChatSuspend.setOnClickListener {
            lifecycleScope.launch {
                sendEventSuspend(
                    event = ChatEvent("李四", "挂起函数发送到 chatBus"),
                    bus = AppBus.chatBus
                )
                showToast("已通过挂起函数发送到 chatBus")
            }
        }

        // 发送标签到 chatBus
        binding.btnSendTagChatBus.setOnClickListener {
            sendTag("chat_online", bus = AppBus.chatBus)
            showToast("已发送标签 chat_online 到 chatBus")
        }

        // 批量发送行情到 pricingBus（模拟实时行情推送）
        binding.btnSendPriceBatch.setOnClickListener {
            lifecycleScope.launch {
                repeat(20) { index ->
                    val price = 150.0 + index * 0.25
                    sendEventSuspend(
                        event = PriceEvent("GOOG", price),
                        bus = AppBus.pricingBus
                    )
                    delay(30)
                }
                showToast("已发送 20 条行情到 pricingBus")
            }
        }

        // 隔离对比测试：同时向全局和 chatBus 发送
        binding.btnIsolationTest.setOnClickListener {
            lifecycleScope.launch {
                sendEventSuspend(ChatEvent("系统", "这条在全局Bus"))
                sendEventSuspend(
                    event = ChatEvent("系统", "这条在chatBus"),
                    bus = AppBus.chatBus
                )
                showToast("已向全局Bus和chatBus各发送一条，查看日志验证隔离")
            }
        }
    }

    private fun performRefresh() {
        Log.d(TAG, "执行刷新操作")
        updateLog("→ 正在刷新...")
    }

    private fun performSync() {
        Log.d(TAG, "执行同步操作")
        updateLog("→ 正在同步...")
    }

    private fun performLogout() {
        Log.d(TAG, "执行登出操作")
        updateLog("→ 正在登出...")
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
        // 重要：取消不绑定生命周期的 Job
        handlerJob?.cancel()
        chatHandlerJob?.cancel()
    }
}
