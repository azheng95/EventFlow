package com.azheng.flow.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.azheng.event.flow.receiveEvent
import com.azheng.event.flow.receiveEventHandler
import com.azheng.event.flow.receiveEventLive
import com.azheng.event.flow.receiveTag
import com.azheng.event.flow.receiveTagHandler
import com.azheng.event.flow.receiveTagLive
import com.azheng.event.flow.sendEvent
import com.azheng.event.flow.sendEventSuspend
import com.azheng.event.flow.sendTag
import com.azheng.event.flow.sendTagSuspend
import com.azheng.flow.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    // 保存 receiveEventHandler 返回的 Job，用于手动取消
    private var handlerJob: Job? = null

    // 保存独立 Bus 的 receiveEventHandler Job，用于手动取消
    private var chatHandlerJob: Job? = null

    // 【新增】保存 receiveTagHandler 返回的 Job，用于手动取消
    private var tagHandlerJob: Job? = null

    // 【新增】自定义 CoroutineScope 示例，需手动管理生命周期
    private val customScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 串行处理测试结果（线程安全集合，防止并发问题）
    private val serialTestResults = CopyOnWriteArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 先注册接收者，再设置发送按钮，确保订阅者在发送前就绑定
        setupReceiveEvents()
        setupSendButtons()
    }

    /**
     * 设置发送按钮点击事件
     */
    private fun setupSendButtons() {
        // ==================== 测试/调试 ====================

        // 压力测试：在主线程协程中运行（receiveEventLive 需要主线程）
        binding.btnBusStress.setOnClickListener {
            lifecycleScope.launch {
                FlowEventBusStressTest().runAllTests()
            }
        }

        // 串行处理验证：快速发送3个事件，验证 receiveEvent 的 collect 是串行处理的
        binding.btnMutex.setOnClickListener {
            serialTestResults.clear()
            lifecycleScope.launch {
                sendEventSuspend(1)
                sendEventSuspend(2)
                sendEventSuspend(3)
            }
        }

        // 【新增】清空日志，方便反复测试观察
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = "日志已清空"
        }

        // ==================== 发送事件示例 ====================

        // 1. 基础事件发送（非生命周期绑定，使用全局 FlowScope）
        binding.btnSendEvent.setOnClickListener {
            sendEvent(LoginEvent("user_001", "张三"))
            showToast("已发送 LoginEvent")
        }

        // 2. 带标签的事件发送（标签用于接收方过滤）
        binding.btnSendEventWithTag.setOnClickListener {
            sendEvent(
                event = MessageEvent("这是一条VIP消息", type = 1),
                tag = "vip_channel"
            )
            showToast("已发送带标签的 MessageEvent")
        }

        // 3. 使用 LifecycleOwner 扩展函数发送（绑定 Activity 生命周期，ON_DESTROY 自动取消）
        binding.btnSendEventLifecycle.setOnClickListener {
            this.sendEvent(
                event = RefreshEvent("user_data", forceRefresh = true),
                tag = "refresh"
            )
            showToast("已发送 RefreshEvent（生命周期感知）")
        }

        // 4. 在协程中使用挂起函数发送（适合已在协程中的场景）
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

        // 【新增】6. 使用自定义 CoroutineScope 发送事件
        //    适合在非 LifecycleOwner、非 lifecycleScope 的场景中使用
        //    注意：customScope 需要在 onDestroy 中手动 cancel
        binding.btnSendCustomScope.setOnClickListener {
            sendEvent(
                event = MessageEvent("自定义 Scope 发送的消息（IO线程）"),
                scope = customScope
            )
            showToast("已通过自定义 CoroutineScope 发送事件")
        }

        // ==================== 发送标签示例 ====================

        // 7. 发送纯标签事件（不携带数据，仅作为信号通知）
        binding.btnSendTag.setOnClickListener {
            sendTag("action_refresh")
            showToast("已发送标签: action_refresh")
        }

        // 8. 使用 LifecycleOwner 扩展函数发送标签
        binding.btnSendTagLifecycle.setOnClickListener {
            this.sendTag("action_logout")
            showToast("已发送标签: action_logout")
        }

        // 9. 在协程中使用挂起函数发送标签
        binding.btnSendTagSuspend.setOnClickListener {
            lifecycleScope.launch {
                sendTagSuspend("action_sync")
                showToast("已通过挂起函数发送标签")
            }
        }

        // 【新增】10. 快速发送多个标签（测试 receiveTagLive onlyReceiveLatest=true）
        //    回到前台后，onlyReceiveLatest=true 的接收者只会处理最后一个标签
        binding.btnSendMultipleTags.setOnClickListener {
            lifecycleScope.launch {
                repeat(5) { index ->
                    sendTagSuspend("status_update_#${index + 1}")
                    delay(30)
                }
                showToast("已快速发送 5 个 status_update 标签")
            }
        }

        // ==================== 串行/并行处理对比示例 ====================

        // 【新增】11. 批量发送 TaskEvent（同时触发串行和并行两个接收者，观察处理顺序差异）
        binding.btnSendTaskBatch.setOnClickListener {
            lifecycleScope.launch {
                repeat(5) { index ->
                    sendEventSuspend(
                        event = TaskEvent(
                            taskId = index + 1,
                            taskName = "任务${index + 1}",
                            // 让前面的任务耗时更长，凸显串行/并行差异
                            processingTimeMs = (5 - index) * 100L
                        ),
                        tag = "task_demo"
                    )
                }
                showToast("已发送 5 个 TaskEvent，观察串行/并行日志")
            }
        }

        // ==================== 独立Bus发送示例 ====================

        // 12. 发送聊天消息到 chatBus（仅 chatBus 的订阅者能收到）
        binding.btnSendChatBus.setOnClickListener {
            sendEvent(
                event = ChatEvent("张三", "你好，这是 chatBus 消息"),
                bus = AppBus.chatBus
            )
            showToast("已发送 ChatEvent 到 chatBus")
        }

        // 13. 发送同类型 ChatEvent 到全局 Bus（验证隔离：chatBus 订阅者收不到）
        binding.btnSendChatGlobal.setOnClickListener {
            sendEvent(ChatEvent("系统", "这是全局 Bus 的 ChatEvent"))
            showToast("已发送 ChatEvent 到全局 Bus")
        }

        // 14. 发送行情到 pricingBus（DROP_OLDEST 策略，旧行情自动丢弃）
        binding.btnSendPriceBus.setOnClickListener {
            lifecycleScope.launch {
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

        // 15. 发送标签到 chatBus（验证标签也支持独立 Bus）
        binding.btnSendTagChatBus.setOnClickListener {
            sendTag("chat_typing", bus = AppBus.chatBus)
            showToast("已发送标签 chat_typing 到 chatBus")
        }

        // 16. 独立 Bus 隔离对比测试：同时向三个 Bus 发送，验证互不干扰
        binding.btnIsolationTest.setOnClickListener {
            lifecycleScope.launch {
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

        // 17. 跳转到 SecondActivity
        binding.btnGoToSecond.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
    }

    /**
     * 设置接收事件
     *
     * 线程说明：
     * - receiveEvent / receiveEventLive / receiveTag / receiveTagLive
     *   绑定 LifecycleOwner 时使用 Dispatchers.Main.immediate，回调已在主线程
     * - receiveEventHandler / receiveTagHandler
     *   不绑定生命周期，使用 Dispatchers.Default，回调在后台线程
     *   如需操作 UI，必须手动切换到主线程
     */
    private fun setupReceiveEvents() {
        // ==================== 串行处理验证 ====================

        // 验证 receiveEvent 的 collect 是串行处理的
        // 即使 delay 不同时间，事件仍按到达顺序依次处理
        receiveEvent<Int> { number ->
            delay((4 - number) * 100L)  // 数字越小，处理越慢
            serialTestResults.add(number)
            Log.d(TAG, "串行处理完成: $number, 当前结果: $serialTestResults")
        }

        // ==================== 接收事件示例 ====================

        // 1. 基础事件接收（生命周期感知，ON_DESTROY 自动取消）
        //    不指定标签 → 接收所有 LoginEvent
        receiveEvent<LoginEvent> { event ->
            Log.d(TAG, "收到 LoginEvent: userId=${event.userId}, userName=${event.userName}")
            updateLog("收到登录事件: ${event.userName}")
        }

        // 2. 带标签过滤的事件接收（只接收 tag="vip_channel" 的 MessageEvent）
        receiveEvent<MessageEvent>("vip_channel") { event ->
            Log.d(TAG, "收到 VIP MessageEvent: ${event.content}")
            updateLog("收到VIP消息: ${event.content}")
        }

        // 3. 多标签过滤的事件接收（匹配 channel_a / channel_b / channel_c 任意一个即可）
        receiveEvent<MessageEvent>("channel_a", "channel_b", "channel_c") { event ->
            Log.d(TAG, "收到多标签 MessageEvent: ${event.content}")
            updateLog("收到多标签消息: ${event.content}")
        }

        // 4. receiveEventLive - 延迟到前台接收（接收所有事件，不丢失）
        //    后台期间事件缓存在 Channel(UNLIMITED) 中，回到前台后按 FIFO 顺序处理
        receiveEventLive<RefreshEvent>(
            "refresh",
            onlyReceiveLatest = false  // 接收所有事件，不会丢失
        ) { event ->
            Log.d(TAG, "前台收到 RefreshEvent: ${event.dataType}")
            updateLog("前台收到刷新事件: ${event.dataType}")
        }

        // 5. receiveEventLive - 只接收最新事件
        //    后台期间使用 Channel(CONFLATED)，只保留最后一个事件
        //    适合位置更新、进度条等只关心最新状态的场景
        receiveEventLive<MessageEvent>(
            onlyReceiveLatest = true  // 回到前台只处理最后一个事件
        ) { event ->
            Log.d(TAG, "前台收到最新 MessageEvent: ${event.content}")
            updateLog("前台收到最新消息: ${event.content}")
        }

        // 【新增】6. receiveEventLive - 多标签 + 前台感知
        //    同时监听 "channel_a" 和 "channel_b" 标签，后台缓存，前台处理
        receiveEventLive<MessageEvent>(
            "channel_a", "channel_b",
            onlyReceiveLatest = false
        ) { event ->
            Log.d(TAG, "前台收到多标签 MessageEvent: ${event.content}")
            updateLog("【LiveData多标签】消息: ${event.content}")
        }

        // 【新增】7. receiveEventLive - 自定义 lifeEvent = ON_STOP
        //    整个接收机制（包括生产者和消费者）在 ON_STOP 时取消
        //    与默认 ON_DESTROY 的区别：Activity 不可见后就完全停止接收
        //    适合只在 Activity 完全可见期间才需要的事件（如动画触发、传感器数据）
        receiveEventLive<RefreshEvent>(
            "refresh",
            lifeEvent = Lifecycle.Event.ON_STOP,  // ON_STOP 时停止整个机制
            onlyReceiveLatest = true
        ) { event ->
            Log.d(TAG, "ON_STOP前有效的 LiveData RefreshEvent: ${event.dataType}")
            updateLog("【ON_STOP前有效】刷新: ${event.dataType}")
        }

        // ==================== 串行/并行处理对比 ====================

        // 【新增】8. receiveEventLive - 串行处理模式（默认）
        //    for 循环天然串行，保证事件按顺序逐个处理
        //    即使前一个任务耗时较长，后续任务也必须等待
        receiveEventLive<TaskEvent>(
            "task_demo",
            onlyReceiveLatest = false,
            serialProcessing = true  // 默认值，显式写出以便对比
        ) { event ->
            Log.d(TAG, "【串行】开始处理: ${event.taskName}")
            delay(event.processingTimeMs)  // 模拟耗时操作
            Log.d(TAG, "【串行】完成处理: ${event.taskName} (耗时${event.processingTimeMs}ms)")
            updateLog("【串行完成】${event.taskName}")
        }

        // 【新增】9. receiveEventLive - 并行处理模式
        //    每个事件启动独立协程，并发处理，性能更好但不保证处理顺序
        //    适合事件之间无依赖关系的场景（如独立的图片下载任务）
        receiveEventLive<TaskEvent>(
            "task_demo",
            onlyReceiveLatest = false,
            serialProcessing = false  // 并行模式：每个事件 launch 独立协程
        ) { event ->
            Log.d(TAG, "【并行】开始处理: ${event.taskName}")
            delay(event.processingTimeMs)  // 模拟耗时操作
            Log.d(TAG, "【并行】完成处理: ${event.taskName} (耗时${event.processingTimeMs}ms)")
            updateLog("【并行完成】${event.taskName}")
        }

        // ==================== 接收标签示例 ====================

        // 10. 基础标签接收（匹配 action_refresh 或 action_sync）
        receiveTag("action_refresh", "action_sync") { tag ->
            Log.d(TAG, "收到标签: $tag")
            updateLog("收到标签: $tag")
        }

        // 11. receiveTagLive - 延迟到前台接收标签
        receiveTagLive(
            "action_logout",
            onlyReceiveLatest = false
        ) { tag ->
            Log.d(TAG, "前台收到标签: $tag")
            updateLog("前台收到标签: $tag")
        }

        // 【新增】12. receiveTag 零标签 - 接收所有标签事件
        //    不传任何标签参数，匹配所有 FlowTag 事件
        //    适合全局标签监控/日志记录
        receiveTag { tag ->
            Log.d(TAG, "【全局标签监控】收到: $tag")
            updateLog("【全局标签监控】$tag")
        }

        // 【新增】13. receiveTagLive - onlyReceiveLatest = true
        //    后台期间多个标签到来时只保留最后一个
        //    适合状态指示类标签（如在线/离线/忙碌，只关心最新状态）
        receiveTagLive(
            "status_update_#1", "status_update_#2", "status_update_#3",
            "status_update_#4", "status_update_#5",
            onlyReceiveLatest = true  // 回到前台只处理最后一个标签
        ) { tag ->
            Log.d(TAG, "前台收到最新状态标签: $tag")
            updateLog("【最新状态标签】$tag")
        }

        // 14. 不绑定生命周期的事件接收（运行在 Dispatchers.Default）
        //    ⚠️ 必须在 onDestroy 中手动取消，否则内存泄漏！
        //    适合不需要 UI 操作的后台处理（如日志记录）
        handlerJob = receiveEventHandler<MessageEvent> { event ->
            // 注意：此回调在后台线程执行，不可直接操作 UI
            Log.d(TAG, "Handler 收到 MessageEvent: ${event.content}")
        }

        // 【新增】15. receiveTagHandler - 不绑定生命周期的标签接收
        //    运行在 Dispatchers.Default（后台线程）
        //    ⚠️ 必须在 onDestroy 中手动取消，否则内存泄漏！
        //    适合后台标签处理（如埋点上报、日志记录）
        tagHandlerJob = receiveTagHandler("action_refresh", "action_sync", "action_logout") { tag ->
            // 注意：此回调在后台线程执行，不可直接操作 UI
            Log.d(TAG, "【TagHandler 后台线程】收到标签: $tag")
        }

        // ==================== 独立Bus接收示例 ====================

        // 16. 接收 chatBus 上的 ChatEvent（全局 Bus 的 ChatEvent 不会触发此回调）
        receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到 ChatEvent: from=${event.from}, msg=${event.message}")
            updateLog("【chatBus】聊天: ${event.from} 说: ${event.message}")
        }

        // 17. 接收全局 Bus 上的 ChatEvent（chatBus 的 ChatEvent 不会触发此回调）
        receiveEvent<ChatEvent> { event ->
            Log.d(TAG, "【全局Bus】收到 ChatEvent: from=${event.from}, msg=${event.message}")
            updateLog("【全局Bus】聊天: ${event.from} 说: ${event.message}")
        }

        // 18. 接收 pricingBus 上的 PriceEvent（使用 receiveEventLive，只关心最新行情）
        receiveEventLive<PriceEvent>(
            onlyReceiveLatest = true,  // 只接收最新行情
            bus = AppBus.pricingBus
        ) { event ->
            Log.d(TAG, "【pricingBus】最新行情: ${event.symbol} = ${event.price}")
            updateLog("【pricingBus】最新行情: ${event.symbol} = $${event.price}")
        }

        // 19. 接收 chatBus 上的标签事件
        receiveTag("chat_typing", bus = AppBus.chatBus) { tag ->
            Log.d(TAG, "【chatBus】收到标签: $tag")
            updateLog("【chatBus】标签: $tag")
        }

        // 20. 隔离验证：分别在 chatBus 和 pricingBus 上接收 MessageEvent
        //     全局 Bus 发送的 MessageEvent 不会出现在这里
        receiveEvent<MessageEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus】收到 MessageEvent: ${event.content}")
            updateLog("【chatBus隔离验证】消息: ${event.content}")
        }

        receiveEvent<MessageEvent>(bus = AppBus.pricingBus) { event ->
            Log.d(TAG, "【pricingBus】收到 MessageEvent: ${event.content}")
            updateLog("【pricingBus隔离验证】消息: ${event.content}")
        }

        // 21. 不绑定生命周期的独立 Bus 事件接收
        //     ⚠️ 必须在 onDestroy 中手动取消！
        chatHandlerJob = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
            Log.d(TAG, "【chatBus Handler】收到: ${event.from} - ${event.message}")
        }
    }

    /**
     * 更新日志显示
     *
     * 使用 runOnUiThread 作为安全保障：
     * - 生命周期绑定的接收者回调已在主线程，runOnUiThread 直接执行（无额外开销）
     * - 如果从后台线程调用，runOnUiThread 会正确 post 到主线程
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
        // 重要：手动取消不绑定生命周期的 Job，避免内存泄漏
        // 生命周期绑定的接收者（receiveEvent/receiveEventLive）会自动取消，无需手动处理
        handlerJob?.cancel()
        chatHandlerJob?.cancel()
        tagHandlerJob?.cancel()

        // 手动取消自定义 CoroutineScope
        customScope.cancel()
    }
}
