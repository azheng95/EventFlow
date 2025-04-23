package com.azheng.flow.demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.azheng.event.flow.receiveEventLive
import com.azheng.event.flow.sendEvent
import com.azheng.flow.demo.databinding.ActivityTestBinding
import dev.androidbroadcast.vbpd.viewBinding

/**
 * @date 2025/4/23.
 * description：
 */
class TestActivity : AppCompatActivity() {

    private val viewBinding: ActivityTestBinding by viewBinding(ActivityTestBinding::bind)

    private val TAG = "TestActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        initView()
    }

    fun initView() {
        viewBinding.btnSendEvent.setOnClickListener {
            val event = viewBinding.etEvent.text.toString()
            if (event.isBlank()) {
                Toast.makeText(this, "请输入事件内容", Toast.LENGTH_SHORT).show()
            } else {
                sendEvent(event)
            }
        }
        viewBinding.btnSendEvent2.setOnClickListener {
            val event = viewBinding.etEvent.text.toString()
            if (event.isBlank()) {
                Toast.makeText(this, "请输入标签事件内容", Toast.LENGTH_SHORT).show()
            } else {
                sendEvent(event, "标签1")
            }
        }
        receiveEventLive<String>(onlyReceiveLatest = true) {
            Log.d(TAG, "Test $it")
        }
    }
}