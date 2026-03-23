package com.azheng.event.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于Kotlin Flow实现的事件总线
 * 用于在应用程序的不同组件间传递事件，采用发布-订阅模式
 *
 * 支持全局默认实例和多个独立实例：
 * - 全局实例：通过 [FlowEventBus.default] 获取，或通过 [FlowEventBus.init] 自定义配置初始化
 * - 独立实例：通过 [FlowEventBus.create] 创建，各实例配置和事件流完全隔离，互不影响
 *
 * @param config 事件总线配置
 */
class FlowEventBus private constructor(config: FlowEventBusConfig) {

    /**
     * 内部可变共享流，用于发布事件
     * MutableSharedFlow允许多个收集器订阅同一个流
     */
    private val _eventFlow: MutableSharedFlow<FlowEvent<*>> = MutableSharedFlow(
        replay = config.replaySize,                       // 缓存的事件数量
        extraBufferCapacity = config.extraBufferCapacity,  // 额外缓冲区容量
        onBufferOverflow = config.bufferOverflow           // 缓冲区溢出策略
    )

    /**
     * 供外部访问的只读流
     * 使用PublishedApi注解使其在内联函数中可见
     */
    @PublishedApi
    internal val eventFlow: SharedFlow<FlowEvent<*>>
        get() = _eventFlow

    /**
     * 发布事件到事件总线
     * @param event 要发布的事件对象
     * @param tag 事件标签，可用于过滤
     */
    suspend fun <T> emitEvent(event: T, tag: String?) {
        _eventFlow.emit(FlowEvent(event, tag))
    }

    companion object {
        /**
         * 全局默认实例
         * 使用 volatile 确保多线程可见性
         */
        @Volatile
        private var _default: FlowEventBus? = null

        /**
         * 标记全局默认事件总线是否已初始化 原子布尔值,更加线程安全
         */
        private val initialized = AtomicBoolean(false)

        /**
         * 获取全局默认实例
         * 如果未初始化，则使用默认配置自动初始化
         * 使用PublishedApi注解使其在内联函数中可见
         */
        @PublishedApi
        internal val default: FlowEventBus
            get() {
                ensureInitialized()
                return _default!!
            }

        /**
         * 初始化全局默认事件总线
         * 使用双重检查锁定模式确保线程安全和性能
         * @param config 事件总线配置，默认使用FlowEventBusConfig.DEFAULT
         * @return 如果是首次初始化返回true，已初始化则返回false
         */
        fun init(config: FlowEventBusConfig = FlowEventBusConfig.DEFAULT): Boolean {
            // 第一次检查（无锁）
            if (initialized.get()) return false

            synchronized(this) {
                // 第二次检查（有锁）
                if (!initialized.get()) {
                    _default = FlowEventBus(config)
                    initialized.set(true)
                    return true
                }
            }
            return false
        }

        /**
         * 确保全局默认事件总线已初始化
         * 如果未初始化，则使用默认配置进行初始化
         */
        private fun ensureInitialized() {
            if (!initialized.get()) {
                init()
            }
        }

        /**
         * 创建独立的事件总线实例
         * 该实例拥有独立的事件流和配置，与全局默认实例及其他独立实例完全隔离
         *
         * @param config 事件总线配置，默认使用FlowEventBusConfig.DEFAULT
         * @return 新的独立FlowEventBus实例
         */
        fun create(config: FlowEventBusConfig = FlowEventBusConfig.DEFAULT): FlowEventBus {
            return FlowEventBus(config)
        }
    }
}
