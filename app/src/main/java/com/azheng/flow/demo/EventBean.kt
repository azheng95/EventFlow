package com.azheng.flow.demo

/**
 * 事件数据类示例
 */
data class EventBean(
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 用户登录事件
 */
data class LoginEvent(
    val userId: String,
    val userName: String,
    val isSuccess: Boolean
)

/**
 * 网络状态事件
 */
data class NetworkEvent(
    val isConnected: Boolean,
    val networkType: String
)

/**
 * 购物车更新事件
 */
data class CartUpdateEvent(
    val itemCount: Int,
    val totalPrice: Double
)
