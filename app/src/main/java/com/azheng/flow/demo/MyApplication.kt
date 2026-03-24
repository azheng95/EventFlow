package com.azheng.flow.demo

import android.app.Application
import com.azheng.event.flow.FlowEventBus
import com.azheng.event.flow.FlowEventBusConfig
import kotlinx.coroutines.channels.BufferOverflow

/**
 * 在 Application 的 onCreate 中初始化全局默认事件总线
 *
 * 说明：
 * - 不调用 init() 也可以，首次使用时会自动以默认配置初始化
 * - 显式调用 init() 的目的是自定义配置，或确保初始化时机可控
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 方式一：使用默认配置初始化（等价于不调用 init）
        // FlowEventBus.init()

        // 方式二：自定义配置初始化（当前值恰好与默认值一致，仅作示例）
        // 如需自定义，修改下方参数即可
        FlowEventBus.init(
            FlowEventBusConfig.Builder()
                .setReplaySize(0)                          // 不保留历史事件
                .setExtraBufferCapacity(2048)               // 缓冲区容量
                .setBufferOverflow(BufferOverflow.SUSPEND)  // 缓冲区满时挂起，保证不丢事件
                .build()
        )
    }
}
