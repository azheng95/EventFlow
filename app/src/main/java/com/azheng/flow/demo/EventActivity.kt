package com.azheng.flow.demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.azheng.event.flow.*
import com.azheng.flow.demo.databinding.ActivityTestBinding

class EventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    private val TAG = "TestActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initEventReceivers()
        initClickListeners()
    }

    /**
     * 初始化事件接收器
     */
    private fun initEventReceivers() {
        // 接收来自 MainActivity 的事件
        receiveEventLive<String>(onlyReceiveLatest = true) {
            Log.d(TAG, "TestActivity 收到: $it")
            showToast("收到: $it")
        }

        // 接收确认事件
        receiveEvent<String>("confirm_from_main") {
            Log.d(TAG, "收到确认: $it")
            binding.tvReceived?.text = "确认: $it"
        }
    }

    /**
     * 初始化点击事件
     */
    private fun initClickListeners() {
        // ==================== 1. 发送普通事件 ====================
        binding.btnSendEvent.setOnClickListener {
            val eventContent = binding.etEvent.text.toString()
            if (eventContent.isBlank()) {
                showToast("请输入事件内容")
            } else {
                sendEvent(EventBean(eventContent))
                showToast("已发送事件: $eventContent")
            }
        }

        // ==================== 2. 发送带标签的事件 ====================
        binding.btnSendEvent2.setOnClickListener {
            val eventContent = binding.etEvent.text.toString()
            if (eventContent.isBlank()) {
                showToast("请输入标签事件内容")
            } else {
                sendEvent(eventContent, "标签1")
                showToast("已发送标签事件: $eventContent")
            }
        }

        // ==================== 3. 发送多个事件（测试队列处理）====================
        binding.btnSendMultiple?.setOnClickListener {
            repeat(5) { index ->
                sendEvent(EventBean("批量事件 #${index + 1}"))
            }
            showToast("已发送5个批量事件")
        }

        // ==================== 4. 发送登录事件 ====================
        binding.btnSendLogin?.setOnClickListener {
            sendEvent(LoginEvent(
                userId = "user_${System.currentTimeMillis()}",
                userName = "测试用户",
                isSuccess = true
            ))
            showToast("已发送登录事件")
        }

        // ==================== 5. 发送网络状态事件 ====================
        binding.btnSendNetwork?.setOnClickListener {
            sendEvent(NetworkEvent(
                isConnected = true,
                networkType = "4G"
            ))
            showToast("已发送网络状态事件")
        }

        // ==================== 6. 发送购物车事件（带标签）====================
        binding.btnSendCart?.setOnClickListener {
            sendEvent(
                CartUpdateEvent(itemCount = 3, totalPrice = 199.00),
                tag = "cart_update"
            )
            showToast("已发送购物车事件")
        }

        // ==================== 7. 发送纯标签 ====================
        binding.btnSendRefreshTag?.setOnClickListener {
            sendTag("refresh")
            showToast("已发送刷新标签")
        }

        binding.btnSendReloadTag?.setOnClickListener {
            sendTag("reload")
            showToast("已发送重载标签")
        }

        // ==================== 8. 发送通知标签 ====================
        binding.btnSendNotification?.setOnClickListener {
            sendTag("notification")
            showToast("已发送通知标签")
        }

        // ==================== 9. 使用生命周期感知发送（绑定到当前Activity）====================
        binding.btnSendWithLifecycle?.setOnClickListener {
            // 这个事件发送会在 Activity 销毁时自动取消（如果还未发送完成）
            this.sendEvent(EventBean("生命周期感知事件"))
            showToast("已发送生命周期感知事件")
        }

        // ==================== 10. 延迟发送多个事件（测试 onlyReceiveLatest）====================
        binding.btnSendDelayed?.setOnClickListener {
            // 使用协程延迟发送
            sendEvent(EventBean("延迟事件1"))
            sendEvent(EventBean("延迟事件2"))
            sendEvent(EventBean("延迟事件3-最新"))
            showToast("已发送3个延迟事件")
        }

        // ==================== 11. 返回并发送事件 ====================
        binding.btnBackWithEvent?.setOnClickListener {
            sendEvent(EventBean("来自TestActivity的事件"))
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
