package com.azheng.event.flow

/**
 * 带版本号的事件包装类
 *
 * 用于 onlyReceiveLatest 模式下安全地判断事件/标签是否为最新，
 * 避免直接对事件对象使用 === 引用比较带来的隐患：
 * - 基本类型（Int、Long 等）经过装箱后可能产生不同引用，也可能因缓存产生相同引用
 * - 字符串字面量被 JVM intern，不同位置的相同字面量 === 为 true
 * - data class 的 copy() 或解构重建会产生不同引用
 *
 * 设计原理：
 * - 每次发送事件都创建新的 Versioned 实例，搭配 AtomicLong 生成单调递增的版本号
 * - observer 端通过比较版本号（Long 值比较）判断是否为最新，语义清晰且不依赖 JVM 引用相等的实现细节
 *
 * @param T 包装的值类型
 * @param value 实际的事件/标签值
 * @param version 单调递增的版本号，用于唯一标识每次发送
 */
@PublishedApi
internal class Versioned<T>(val value: T, val version: Long)
