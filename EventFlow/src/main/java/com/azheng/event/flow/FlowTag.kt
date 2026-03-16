package com.azheng.event.flow

/**
 * 标签事件标记对象
 *
 * 用于标识纯标签事件，不包含实际数据
 * 当只需要通过标签触发行为而不需要传递数据时使用
 * 使用 object 单例模式，避免创建不必要的实例
 */
internal object FlowTag
