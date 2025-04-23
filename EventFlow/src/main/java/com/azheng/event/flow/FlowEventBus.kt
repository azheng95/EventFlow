package com.azheng.event.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 基于Kotlin Flow实现的事件总线
 * 用于在应用程序的不同组件间传递事件，采用发布-订阅模式
 */
object FlowEventBus {
    /**
     * 内部可变共享流，用于发布事件
     * MutableSharedFlow允许多个收集器订阅同一个流
     */
    private lateinit var _eventFlow: MutableSharedFlow<FlowEvent<*>>

    /**
     * 供外部访问的只读流
     * 使用PublishedApi注解使其在内联函数中可见
     */
    @PublishedApi
    internal val eventFlow: SharedFlow<FlowEvent<*>>
        get() {
            ensureInitialized()  // 确保已初始化
            return _eventFlow
        }

    /**
     * 标记事件总线是否已初始化
     */
    private var initialized = false

    /**
     * 初始化事件总线
     * 使用Synchronized确保线程安全
     * @param config 事件总线配置，默认使用FlowEventBusConfig.DEFAULT
     */
    @Synchronized
    fun init(config: FlowEventBusConfig = FlowEventBusConfig.DEFAULT) {
        if (!initialized) {
            _eventFlow = MutableSharedFlow(
                replay = config.replaySize,            // 缓存的事件数量
                extraBufferCapacity = config.extraBufferCapacity,  // 额外缓冲区容量
                onBufferOverflow = config.bufferOverflow           // 缓冲区溢出策略
            )
            initialized = true
        }
    }

    /**
     * 确保事件总线已初始化
     * 如果未初始化，则使用默认配置进行初始化
     */
    private fun ensureInitialized() {
        if (!initialized) {
            init()
        }
    }

    /**
     * 发布事件到事件总线
     * @param event 要发布的事件对象
     * @param tag 事件标签，可用于过滤
     */
    suspend fun <T> emitEvent(event: T, tag: String?) {
        ensureInitialized()
        _eventFlow.emit(FlowEvent(event, tag))
    }

}
