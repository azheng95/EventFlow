package com.azheng.flow.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.azheng.event.flow.receiveEvent
import com.azheng.event.flow.receiveEventLive
import com.azheng.event.flow.sendEvent
import com.azheng.flow.demo.databinding.ActivityMainBinding
import dev.androidbroadcast.vbpd.viewBinding

/**
 * @date 2025/4/23.
 * description：
 */
class MainActivity : AppCompatActivity() {

    private val viewBinding: ActivityMainBinding by viewBinding(ActivityMainBinding::bind)
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()

    }

    fun initView() {
        // 接收事件
        receiveEvent<String> {
            Log.d(TAG, "receiveEvent $it")
            Toast.makeText(this@MainActivity, "接收到事件:  $it", Toast.LENGTH_SHORT).show()
            viewBinding.tvEvent.text = it
        }
        receiveEventLive<String>(onlyReceiveLatest = true) {
            Log.d(TAG, "receiveEventLive $it")
            Toast.makeText(this@MainActivity, "接收到事件: Live $it", Toast.LENGTH_SHORT).show()
            viewBinding.tvEvent2.text = it
        }
        // 接收标签事件
        receiveEvent<String>("标签1", "标签2") {
            Log.d(TAG, "receiveEvent 标签事件: $it")
            Toast.makeText(this@MainActivity, "接收标签事件:  $it", Toast.LENGTH_SHORT).show()
            viewBinding.tvEvent3.text = it
        }
        // 接收标签事件
        receiveEventLive<String>("标签1", "标签2",onlyReceiveLatest = true) {
            Log.d(TAG, "receiveEventLive 标签事件: $it")
            Toast.makeText(this@MainActivity, "接收标签事件: Live  $it", Toast.LENGTH_SHORT).show()
            viewBinding.tvEvent4.text = it
        }
        viewBinding.btnOpenAct.setOnClickListener {
            startActivity(Intent(this@MainActivity, TestActivity::class.java))
        }
        viewBinding.btnSendToCurrent.setOnClickListener {
          sendEvent("发送事件给当前页面")
        }
    }
}
