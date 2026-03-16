package com.azheng.event.flow

/**
 * Flow承载事件的模型
 *
 * 这是事件总线系统的核心数据结构，用于封装事件对象和可选的标签
 *
 * @param T 事件的泛型类型，允许传递任意类型的事件对象
 * @param event 实际的事件对象，包含需要传递的数据
 * @param tag 可选的事件标签，用于事件分类和过滤，便于接收方选择性地处理事件
 */
@PublishedApi
internal class FlowEvent<T>(val event: T, val tag: String? = null)
