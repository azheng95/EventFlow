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

    // 不绑定生命周期的 Job，必须手动取消
    private var handlerJob: Job? = null
    private var chatHandlerJob: Job? = null

    // 【新增】receiveTagHandler 的 Job
    private var tagHandlerJob: Job? = null
    private var chatTagHandlerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 先注册接收者，再设置发送按钮
        setupReceiveEvents()
        setupSendButtons()
    }

    /**
     * 设置接收事件 - 展示各种接收方式
     *
     * 线程说明：
     * - 生命周期绑定的接收者回调在 Dispatchers.Main.immediate（主线程）
     * - receiveEventHandler / receiveTagHandler 回调在 Dispatchers.Default（后台线程）
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

        // ==================== 前台感知接收（receiveEventLive） ====================

        // 4. 后台事件延迟到前台处理（接收所有事件）
        //    场景：App 在后台时收到多条消息，回到前台时按 FIFO 顺序统一处理
        receiveEventLive<RefreshEvent>(
            onlyReceiveLatest = false  // false: 使用 Channel(UNLIMITED)，处理所有事件
        ) { event ->
            Log.d(TAG, "LiveData收到 RefreshEvent: ${event.dataType}")
            updateLog("【LiveData】刷新: ${event.dataType}")
        }

        // 5. 只处理最新事件
        //    场景：用户位置更新，只关心最新位置，旧位置无意义
        receiveEventLive<MessageEvent>(
            "location_update",
            onlyReceiveLatest = true  // true: 使用 Channel(CONFLATED)，只保留最后一个
        ) { event ->
            updateLog("【最新位置】${event.content}")
        }

        // 【新增】6. receiveEventLive - 自定义 lifeEvent = ON_STOP + 并行处理
        //    ON_STOP 时整个机制取消（包括生产者和消费者）
        //    serialProcessing = false：每个事件启动独立协程并发处理
        //    适合 Activity 可见期间的独立并发任务（如批量图片预加载）
        receiveEventLive<TaskEvent>(
            "task_demo",
            lifeEvent = Lifecycle.Event.ON_STOP,
            onlyReceiveLatest = false,
            serialProcessing = false  // 并行模式
        ) { event ->
            Log.d(TAG, "【并行-ON_STOP】开始: ${event.taskName}")
            delay(event.processingTimeMs)
            Log.d(TAG, "【并行-ON_STOP】完成: ${event.taskName}")
            updateLog("【并行-ON_STOP完成】${event.taskName}")
        }

        // ==================== 标签接收 ====================

        // 7. 接收指定标签，根据标签执行不同操作
        receiveTag("action_refresh", "action_sync", "action_logout") { tag ->
            Log.d(TAG, "收到标签: $tag")
            updateLog("标签事件: $tag")

            when (tag) {
                "action_refresh" -> performRefresh()
                "action_sync" -> performSync()
                "action_logout" -> performLogout()
            }
        }

        // 8. receiveTagLive - 后台标签延迟到前台处理
        receiveTagLive(
            "background_task_complete",
            onlyReceiveLatest = false
        ) { tag ->
            updateLog("【LiveData标签】$tag")
        }

        // 【新增】9. receiveTagLive - onlyReceiveLatest = true
        //    后台期间多个标签到来只保留最后一个
        //    适合状态指示类（如最新同步状态），只关心最终结果
        receiveTagLive(
            "status_update_#1", "status_update_#2", "status_update_#3",
            "status_update_#4", "status_update_#5",
            onlyReceiveLatest = true
        ) { tag ->
            Log.d(TAG, "前台收到最新状态标签: $tag")
            updateLog("【最新状态标签】$tag")
        }

        // 【新增】10. receiveTag 零标签 - 接收所有标签事件（全局标签监控）
        receiveTag { tag ->
            Log.d(TAG, "【全局标签监控】收到: $tag")
        }

        // ==================== 自定义生命周期事件 ====================

        // 11. 指定在 ON_STOP 时停止接收（而不是默认的 ON_DESTROY）
        //     适合只需在可见期间接收的场景
        receiveEvent<MessageEvent>(
            lifeEvent = Lifecycle.Event.ON_STOP
        ) { event ->
            Log.d(TAG, "ON_STOP前有效: ${event.content}")
        }

        // ==================== 不绑定生命周期的接收 ====================

        // 12. receiveEventHandler - 运行在 Dispatchers.Default
        //     ⚠️ 必须在 onDestroy 中手动取消！回调在后台线程，不可直接操作 UI
        handlerJob = receiveEventHandler<LoginEvent> { event ->
            Log.d(TAG, "Handler收到: ${event.userName}")
        }

        // 【新增】13. receiveTagHandler - 不绑定生命周期的标签接收
        //     运行在 Dispatchers.Default（后台线程）
        //     ⚠️ 必须在 onDestroy 中手动取消！
        //     适合后台标签处理（如埋点上报）
        tagHandlerJob = receiveTagHandler("action_refresh", "action_sync") { tag ->
            Log.d(TAG, "【TagHandler 后台】收到标签: $tag")
        }

        // ==================== 独立Bus接收示例 ====================

        // 14. 接收 chatBus 上的 ChatEvent（全局 Bus 的 ChatEvent 不会触发）
        receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到: ${event.from} - ${event.message}")
            updateLog("【chatBus】${event.from}: ${event.message}")
        }

        // 15. 接收全局 Bus 上的 ChatEvent（chatBus 的 ChatEvent 不会触发，验证隔离）
        receiveEvent<ChatEvent> { event ->
            Log.d(TAG, "【全局Bus】收到 ChatEvent: ${event.from} - ${event.message}")
            updateLog("【全局Bus-Chat】${event.from}: ${event.message}")
        }

        // 16. 接收 pricingBus 上的 PriceEvent（始终活跃，后台也能接收）
        //     如需后台持久化行情数据，用 receiveEvent 而不是 receiveEventLive
        receiveEvent<PriceEvent>(bus = AppBus.pricingBus) { event ->
            Log.d(TAG, "【pricingBus】行情: ${event.symbol} = ${event.price}")
            updateLog("【pricingBus】${event.symbol}: $${event.price}")
        }

        // 17. 使用 receiveEventLive 接收 chatBus 上的消息（后台延迟到前台刷新 UI）
        receiveEventLive<ChatEvent>(
            onlyReceiveLatest = false,
            bus = AppBus.chatBus
        ) { event ->
            Log.d(TAG, "【chatBus-LiveData】收到: ${event.from} - ${event.message}")
            updateLog("【chatBus-Live】${event.from}: ${event.message}")
        }

        // 18. 接收 chatBus 上的标签
        receiveTag("chat_typing", "chat_online", bus = AppBus.chatBus) { tag ->
            Log.d(TAG, "【chatBus】标签: $tag")
            updateLog("【chatBus标签】$tag")
        }

        // 19. receiveTagLive 接收 chatBus 上的标签（后台延迟到前台）
        receiveTagLive(
            "chat_new_member",
            onlyReceiveLatest = false,
            bus = AppBus.chatBus
        ) { tag ->
            updateLog("【chatBus-TagLive】$tag")
        }

        // 20. 不绑定生命周期的独立 Bus 事件接收
        //     ⚠️ 必须在 onDestroy 中手动取消！
        chatHandlerJob = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus Handler】收到: ${event.from} - ${event.message}")
        }

        // 【新增】21. receiveTagHandler - 不绑定生命周期的独立 Bus 标签接收
        //     ⚠️ 必须在 onDestroy 中手动取消！
        chatTagHandlerJob = receiveTagHandler("chat_typing", "chat_online", bus = AppBus.chatBus) { tag ->
            Log.d(TAG, "【chatBus TagHandler 后台】收到标签: $tag")
        }
    }

    /**
     * 设置发送按钮 - 从 SecondActivity 发送事件（两个 Activity 的接收者都能收到）
     */
    private fun setupSendButtons() {
        // ==================== 全局 Bus 发送 ====================

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

        // 批量发送位置更新（测试 onlyReceiveLatest：只有最后一条会被处理）
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

        // 模拟后台任务完成（发送多个标签）
        binding.btnBackgroundTaskComplete.setOnClickListener {
            lifecycleScope.launch {
                repeat(3) { index ->
                    delay(100)
                    sendTagSuspend("background_task_complete")
                }
                showToast("已发送 3 次后台任务完成标签")
            }
        }

        // ==================== 新增示例 ====================

        // 【新增】批量发送 TaskEvent（触发 MainActivity/SecondActivity 的串行/并行处理对比）
        binding.btnSendTaskBatch.setOnClickListener {
            lifecycleScope.launch {
                repeat(5) { index ->
                    sendEventSuspend(
                        event = TaskEvent(
                            taskId = index + 1,
                            taskName = "任务${index + 1}",
                            processingTimeMs = (5 - index) * 100L
                        ),
                        tag = "task_demo"
                    )
                }
                showToast("已发送 5 个 TaskEvent")
            }
        }

        // 【新增】快速发送多个标签（测试 receiveTagLive onlyReceiveLatest=true）
        binding.btnSendMultipleTags.setOnClickListener {
            lifecycleScope.launch {
                repeat(5) { index ->
                    sendTagSuspend("status_update_#${index + 1}")
                    delay(30)
                }
                showToast("已快速发送 5 个 status_update 标签")
            }
        }

        // 【新增】发送标签到 pricingBus（验证标签在任何独立 Bus 上都可用）
        binding.btnSendTagPricingBus.setOnClickListener {
            sendTag("price_alert", bus = AppBus.pricingBus)
            showToast("已发送标签 price_alert 到 pricingBus")
        }

        // 【新增】清空日志
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = "日志已清空"
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

        // 发送行情到 pricingBus（使用 LifecycleOwner 扩展函数，绑定生命周期）
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

        // 隔离对比测试：同时向全局 Bus 和 chatBus 发送同类型事件
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

    /**
     * 更新日志显示
     */
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
        // 重要：取消不绑定生命周期的 Job，避免内存泄漏
        handlerJob?.cancel()
        chatHandlerJob?.cancel()
        tagHandlerJob?.cancel()
        chatTagHandlerJob?.cancel()
    }
}
