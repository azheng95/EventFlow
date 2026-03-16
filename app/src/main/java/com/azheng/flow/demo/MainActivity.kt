package com.azheng.flow.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.azheng.event.flow.*
import com.azheng.flow.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.Job

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    // 保存 Job 引用，用于手动取消（可选）
    private var eventJob: Job? = null
    private var tagJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initEventReceivers()
        initClickListeners()
    }

    /**
     * 初始化所有事件接收器
     */
    private fun initEventReceivers() {
        // ==================== 1. 接收事件（不带标签）====================
        // 接收所有 EventBean 类型的事件
        receiveEvent<EventBean> {
            Log.d(TAG, "receiveEvent: $it")
            showToast("接收到事件: ${it.name}")
            binding.tvEvent.text = "事件: ${it.name}"
        }

        // ==================== 2. 使用 LiveData 接收事件（前台可见时处理）====================
        // onlyReceiveLatest = true: 只处理最新事件
        receiveEventLive<EventBean>(onlyReceiveLatest = true) {
            Log.d(TAG, "receiveEventLive (latest): $it")
            showToast("Live事件(最新): ${it.name}")
            binding.tvEvent2.text = "Live事件: ${it.name}"
        }

        // ==================== 3. 带标签的事件接收 ====================
        // 只接收带有 "标签1" 或 "标签2" 的 String 类型事件
        receiveEvent<String>("标签1", "标签2") {
            Log.d(TAG, "receiveEvent 标签事件: $it")
            showToast("标签事件: $it")
            binding.tvEvent3.text = "标签事件: $it"
        }

        // ==================== 4. LiveData + 标签的事件接收 ====================
        receiveEventLive<String>("标签1", "标签2", onlyReceiveLatest = true) {
            Log.d(TAG, "receiveEventLive 标签事件: $it")
            showToast("Live标签事件: $it")
            binding.tvEvent4.text = "Live标签事件: $it"
        }

        // ==================== 5. 接收登录事件 ====================
        receiveEvent<LoginEvent> { event ->
            Log.d(TAG, "收到登录事件: $event")
            val msg = if (event.isSuccess) {
                "用户 ${event.userName} 登录成功"
            } else {
                "登录失败"
            }
            showToast(msg)
        }

        // ==================== 6. 接收网络状态事件 ====================
        receiveEventLive<NetworkEvent>(onlyReceiveLatest = true) { event ->
            Log.d(TAG, "网络状态变化: $event")
            val status = if (event.isConnected) "已连接(${event.networkType})" else "已断开"
            showToast("网络: $status")
        }

        // ==================== 7. 接收购物车更新事件（带标签）====================
        receiveEvent<CartUpdateEvent>("cart_update") { event ->
            Log.d(TAG, "购物车更新: ${event.itemCount} 件, 总价: ${event.totalPrice}")
            showToast("购物车: ${event.itemCount}件, ¥${event.totalPrice}")
        }

        // ==================== 8. 纯标签接收（无数据，仅触发行为）====================
        receiveTag("refresh", "reload") { tag ->
            Log.d(TAG, "收到标签: $tag")
            when (tag) {
                "refresh" -> refreshData()
                "reload" -> reloadPage()
            }
        }

        // ==================== 9. LiveData 标签接收 ====================
        receiveTagLive("notification", onlyReceiveLatest = false) { tag ->
            Log.d(TAG, "收到通知标签: $tag")
            showToast("收到通知: $tag")
        }

        // ==================== 10. 指定生命周期事件停止接收 ====================
        // 在 ON_STOP 时停止接收（而不是默认的 ON_DESTROY）
        receiveEvent<String>(
            "temporary_tag",
            lifeEvent = Lifecycle.Event.ON_STOP
        ) {
            Log.d(TAG, "临时事件（ON_STOP停止）: $it")
        }

        // ==================== 11. 保存 Job 引用，用于手动取消 ====================
        eventJob = receiveEvent<String>("manual_cancel") {
            Log.d(TAG, "可手动取消的事件: $it")
        }
    }

    /**
     * 初始化点击事件
     */
    private fun initClickListeners() {
        // 打开 TestActivity
        binding.btnOpenAct.setOnClickListener {
            startActivity(Intent(this, EventActivity::class.java))
        }

        // 发送事件给当前页面
        binding.btnSendToCurrent.setOnClickListener {
            sendEvent("发送事件给当前页面")
        }

        // 更多发送示例按钮（需要在布局中添加对应的按钮）
        binding.btnSendLogin?.setOnClickListener {
            // 发送登录事件
            sendEvent(LoginEvent("user123", "张三", true))
        }

        binding.btnSendNetwork?.setOnClickListener {
            // 发送网络状态事件
            sendEvent(NetworkEvent(true, "WiFi"))
        }

        binding.btnSendCart?.setOnClickListener {
            // 发送带标签的购物车事件
            sendEvent(CartUpdateEvent(5, 299.99), "cart_update")
        }

        binding.btnSendTag?.setOnClickListener {
            // 发送纯标签
            sendTag("refresh")
        }

        binding.btnCancelJob?.setOnClickListener {
            // 手动取消事件接收
            eventJob?.cancel()
            showToast("已取消事件接收")
        }
    }

    private fun refreshData() {
        Log.d(TAG, "执行刷新操作")
        showToast("正在刷新...")
    }

    private fun reloadPage() {
        Log.d(TAG, "执行重新加载操作")
        showToast("正在重新加载...")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
