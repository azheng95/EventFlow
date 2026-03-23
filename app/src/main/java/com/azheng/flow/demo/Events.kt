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
