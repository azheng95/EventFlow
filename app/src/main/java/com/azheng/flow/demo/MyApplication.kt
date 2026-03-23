package com.azheng.flow.demo

import android.app.Application
import com.azheng.event.flow.FlowEventBus
import com.azheng.event.flow.FlowEventBusConfig
import kotlinx.coroutines.channels.BufferOverflow

// 在 Application 的 onCreate 中初始化
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 不初始化会使用默认配置
        FlowEventBus.init(
            FlowEventBusConfig.Builder()
                .setReplaySize(0)
                .setExtraBufferCapacity(2048)
                .setBufferOverflow(BufferOverflow.SUSPEND)
                .build()
        )
    }
}
