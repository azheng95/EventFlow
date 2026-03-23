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
        /**
         * 重放事件数量
         * 默认为0，不保留历史事件
         * 如果设置为N，新订阅者会立即收到最近N条事件
         */
        private var replaySize: Int = 0

        /**
         * 额外的缓冲区容量
         * 默认为2048，足以应对大多数高并发场景
         * - 普通应用：1024 即可
         * - IM/实时数据应用：建议 2048-4096
         * - 极限高频场景：可设置更大值
         */
        private var extraBufferCapacity: Int = 2048

        /**
         * 缓冲区溢出策略
         * 默认为 SUSPEND，保证事件不丢失（推荐大多数场景）
         *
         * 策略对比：
         * - SUSPEND（默认）：缓冲区满时挂起发送者，保证事件不丢失，适合 IM、订单、支付等场景
         * - DROP_OLDEST：丢弃最旧的事件，适合只关心最新状态的场景（位置更新、进度条等）
         * - DROP_LATEST：丢弃最新的事件，极少使用
         */
        private var bufferOverflow: BufferOverflow = BufferOverflow.SUSPEND

        /**
         * 设置可以重放给新订阅者的事件数量
         *
         * 使用场景：
         * - 0（默认）：新订阅者只接收订阅后的事件
         * - 1：新订阅者可以立即获取最近一条事件（如当前状态）
         * - N：新订阅者可以获取最近N条历史事件
         *
         * @param replaySize 重放事件数量，必须 >= 0
         * @return Builder实例，支持链式调用
         */
        fun setReplaySize(replaySize: Int): Builder = apply {
            require(replaySize >= 0) { "replaySize must be >= 0" }
            this.replaySize = replaySize
        }

        /**
         * 设置额外的缓冲区容量
         *
         * 推荐值：
         * - 普通应用：1024
         * - 高并发应用：2048（默认）
         * - 极限场景：4096 或更大
         *
         * @param capacity 缓冲区容量，必须 > 0
         * @return Builder实例，支持链式调用
         */
        fun setExtraBufferCapacity(capacity: Int): Builder = apply {
            require(capacity > 0) { "extraBufferCapacity must be > 0" }
            this.extraBufferCapacity = capacity
        }

        /**
         * 设置缓冲区溢出策略
         *
         * 策略选择指南：
         *
         * SUSPEND（默认推荐，适合90%场景）：
         * - IM聊天消息、订单状态、支付通知、数据同步
         * - 特点：宁可变慢，不丢失任何事件
         *
         * DROP_OLDEST（适合只关心最新状态）：
         * - 实时位置更新、传感器数据、进度条、股票行情
         * - 特点：丢弃旧数据，保留最新状态
         *
         * DROP_LATEST（极少使用）：
         * - 需要保护已有数据，拒绝新数据的特殊场景
         * - 特点：缓冲区满时丢弃新事件
         *
         * @param overflow 溢出策略
         * @return Builder实例，支持链式调用
         */
        fun setBufferOverflow(overflow: BufferOverflow): Builder = apply {
            this.bufferOverflow = overflow
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
         * 默认配置（适合大多数场景）
         *
         * 默认值说明：
         * - replaySize = 0：不保留历史事件，新订阅者只接收订阅后的事件
         * - extraBufferCapacity = 2048：足够大的缓冲区，应对高并发场景
         * - bufferOverflow = SUSPEND：保证事件不丢失，适合IM、订单、支付等关键业务
         *
         * 如需自定义，请使用 Builder：
         * ```
         * FlowEventBusConfig.Builder()
         *     .setReplaySize(0)
         *     .setExtraBufferCapacity(4096)
         *     .setBufferOverflow(BufferOverflow.DROP_OLDEST)
         *     .build()
         * ```
         */
        val DEFAULT = Builder().build()
    }
}
