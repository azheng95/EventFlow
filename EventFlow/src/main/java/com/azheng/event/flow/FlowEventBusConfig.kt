package com.azheng.event.flow

import kotlinx.coroutines.channels.BufferOverflow

/**
 * FlowEventBusConfig - 事件总线配置类
 * 使用私有构造函数防止直接实例化，推荐使用Builder模式创建
 *
 * @param replaySize 新订阅者可以接收的历史事件数量
 * @param extraBufferCapacity 额外的缓冲区容量，用于非阻塞发射
 * @param bufferOverflow 缓冲区溢出时的处理策略
 */
class FlowEventBusConfig private constructor(
    val replaySize: Int,
    val extraBufferCapacity: Int,
    val bufferOverflow: BufferOverflow
) {
    /**
     * Builder类 - 使用构建器模式创建FlowEventBusConfig实例
     * 提供流畅的API来设置各种配置参数
     */
    class Builder {
        private var replaySize: Int = 0                            // 默认不保留历史事件
        private var extraBufferCapacity: Int = 102400              // 默认缓冲区容量
        private var bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST  // 默认溢出策略

        /**
         * 设置可以重放给新订阅者的事件数量
         * @param replaySize 重放事件数量
         * @return Builder实例，支持链式调用
         */
        fun setReplaySize(replaySize: Int): Builder {
            this.replaySize = replaySize
            return this
        }

        /**
         * 设置额外的缓冲区容量
         * @param capacity 缓冲区容量
         * @return Builder实例，支持链式调用
         */
        fun setExtraBufferCapacity(capacity: Int): Builder {
            this.extraBufferCapacity = capacity
            return this
        }

        /**
         * 设置缓冲区溢出策略
         * @param overflow 溢出策略（如DROP_OLDEST, DROP_LATEST, SUSPEND）
         * @return Builder实例，支持链式调用
         */
        fun setBufferOverflow(overflow: BufferOverflow): Builder {
            this.bufferOverflow = overflow
            return this
        }

        /**
         * 构建FlowEventBusConfig实例
         * @return 根据当前设置创建的FlowEventBusConfig对象
         */
        fun build(): FlowEventBusConfig {
            return FlowEventBusConfig(replaySize, extraBufferCapacity, bufferOverflow)
        }
    }

    companion object {

        /**
         * 默认配置
         * 使用Builder创建的默认配置实例
         */
        val DEFAULT = Builder().build()
    }
}
