package com.azheng.flow.demo

import android.app.Application
import com.azheng.event.flow.FlowEventBus
import com.azheng.event.flow.FlowEventBusConfig
import kotlinx.coroutines.channels.BufferOverflow

// 在 Application 的 onCreate 中初始化
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 使用自定义配置
        val config = FlowEventBusConfig.Builder()
            .setReplaySize(0)
            .setExtraBufferCapacity(1024) // 设置较小的缓冲区
            .setBufferOverflow(BufferOverflow.DROP_OLDEST) // 更改溢出策略
            .build()

        FlowEventBus.init(config)
    }
}
