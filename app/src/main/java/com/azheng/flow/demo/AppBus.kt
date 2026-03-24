package com.azheng.flow.demo

import com.azheng.event.flow.FlowEventBus
import com.azheng.event.flow.FlowEventBusConfig
import kotlinx.coroutines.channels.BufferOverflow

/**
 * 应用级独立Bus实例
 * 各Bus实例完全隔离，互不影响，也不影响全局默认Bus
 *
 * 使用方式：
 * - 发送: sendEvent(event, bus = AppBus.chatBus)
 * - 接收: receiveEvent<T>(bus = AppBus.chatBus) { ... }
 *
 * 优化：使用 lazy 延迟初始化，首次访问时才创建实例，
 * 避免 object 初始化时一次性创建所有 Bus
 */
object AppBus {

    /**
     * 聊天专用Bus
     * - 使用 SUSPEND 策略，保证聊天消息不丢失
     * - 较大的缓冲区，应对聊天高并发场景
     */
    val chatBus: FlowEventBus by lazy {
        FlowEventBus.create(
            FlowEventBusConfig.Builder()
                .setExtraBufferCapacity(4096)
                .setBufferOverflow(BufferOverflow.SUSPEND)
                .build()
        )
    }

    /**
     * 行情专用Bus
     * - 使用 DROP_OLDEST 策略，只关心最新价格，旧行情自动丢弃
     * - replaySize = 1，新订阅者可以立即获取最近一条行情
     */
    val pricingBus: FlowEventBus by lazy {
        FlowEventBus.create(
            FlowEventBusConfig.Builder()
                .setReplaySize(1)
                .setExtraBufferCapacity(1024)
                .setBufferOverflow(BufferOverflow.DROP_OLDEST)
                .build()
        )
    }
}
