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
