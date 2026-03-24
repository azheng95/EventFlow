package com.azheng.flow.demo

/**
 * 用户登录事件
 */
data class LoginEvent(
    val userId: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 消息事件
 */
data class MessageEvent(
    val content: String,
    val type: Int = 0  // 0: 普通消息, 1: 系统消息
)

/**
 * 数据刷新事件
 */
data class RefreshEvent(
    val dataType: String,
    val forceRefresh: Boolean = false
)

/**
 * 聊天消息事件（用于独立 chatBus 示例）
 */
data class ChatEvent(
    val from: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 行情价格事件（用于独立 pricingBus 示例）
 */
data class PriceEvent(
    val symbol: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 批处理任务事件（用于演示 serialProcessing 串行/并行处理模式对比）
 *
 * 使用方式：
 * - 同时注册 serialProcessing=true 和 serialProcessing=false 的接收者
 * - 发送多个 TaskEvent，观察两种模式下的处理顺序差异
 */
data class TaskEvent(
    val taskId: Int,
    val taskName: String,
    val processingTimeMs: Long = 200L  // 模拟处理耗时，用于凸显串行/并行差异
)
