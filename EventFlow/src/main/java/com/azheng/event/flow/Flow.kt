/*
 * Copyright (C) 2018 Drake, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.azheng.event.flow

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * 事件总线实现
 * 基于Kotlin Flow实现的事件总线
 * 用于在应用程序的不同组件间传递事件，采用发布-订阅模式
 */

/**
 * 对外暴露的只读事件流
 * 使用@PublishedApi注解允许内联函数访问此内部属性
 */
@PublishedApi
internal val eventFlow = FlowEventBus.eventFlow

private suspend fun <T> emitEvent(event: T, tag: String?) {
    FlowEventBus.emitEvent(event, tag)
}

/**
 * 生命周期感知的事件发送函数
 * 将事件的生命周期与组件(如Activity或Fragment)的生命周期绑定
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY，表示组件销毁时取消事件
 * @return 返回Job对象，可用于手动取消
 */
fun <T> LifecycleOwner.sendEvent(
    event: T,
    tag: String? = null,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY
) = FlowScope(
    this,
    lifeEvent
).launch {
    emitEvent(event, tag)
}

/**
 * 非生命周期感知的事件发送函数
 * 可在任何协程作用域中发送事件
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 * @param scope 协程作用域，默认使用FlowScope
 * @return 返回Job对象，可用于手动取消
 */
fun <T> sendEvent(event: T, tag: String? = null, scope: CoroutineScope = FlowScope()) =
    scope.launch {
        emitEvent(event, tag)
    }

/**
 * 挂起函数版本的事件发送
 * 适用于已在协程中的调用场景
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 */
suspend fun <T> sendEventSuspend(event: T, tag: String? = null) {
    emitEvent(event, tag)
}

/**
 * 生命周期感知的标签发送函数
 * 发送一个只包含标签的特殊事件(FlowTag)
 *
 * @param tag 要发送的标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @return 返回Job对象，可用于手动取消
 */
fun LifecycleOwner.sendTag(
    tag: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY
) = sendEvent(FlowTag(), tag, lifeEvent)

/**
 * 非生命周期感知的标签发送函数
 *
 * @param tag 要发送的标签
 * @param scope 协程作用域，默认使用FlowScope
 * @return 返回Job对象，可用于手动取消
 */
fun sendTag(
    tag: String?,
    scope: CoroutineScope = FlowScope()
) = sendEvent(FlowTag(), tag, scope)

/**
 * 挂起函数版本的标签发送
 *
 * @param tag 要发送的标签
 */
suspend fun sendTagSuspend(tag: String?) = sendEventSuspend(FlowTag(), tag)

/**
 * 接收事件
 * 根据事件类型和标签过滤接收特定事件
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
inline fun <reified T> LifecycleOwner.receiveEvent(
    vararg tags: String? = emptyArray(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        eventFlow
            .filter { bus -> bus.event is T && (tags.isEmpty() || tags.contains(bus.tag)) }
            .collect { bus -> block(bus.event as T) }
    }
}

/**
 * 使用LiveData将消息延迟到前台接收
 * 确保事件处理在UI可见时进行，避免在后台处理事件
 *
 * @param T 要接收的事件类型
 * @param tags 可接受的标签数组
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
inline fun <reified T> LifecycleOwner.receiveEventLive(
    vararg tags: String? = arrayOf(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        eventFlow
            .filter { bus -> bus.event is T && (tags.isEmpty() || tags.contains(bus.tag)) }
            .collect { bus ->
                val liveData = MutableLiveData<T>()
                liveData.observe(this@receiveEventLive) { coroutineScope.launch { block(it) } }
                liveData.value = bus.event as T
            }
    }
}

/**
 * 使用LiveData将消息延迟到前台接收，支持控制是否只接收最新事件
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签，如果标签为零个则匹配事件对象即可成功接收，如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
inline fun <reified T> LifecycleOwner.receiveEventLive(
    vararg tags: String? = arrayOf(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    onlyReceiveLatest: Boolean = false,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    // 用于存储最后一次事件的容器
    val latestEventContainer = AtomicReference<T?>(null)
    return coroutineScope.launch {
        eventFlow
            .filter { bus -> bus.event is T && (tags.isEmpty() || tags.contains(bus.tag)) }
            .collect { bus ->
                val liveData = if (onlyReceiveLatest) {
                    // 如果设置了只接收最新值，使用单一LiveData实例
                    latestEventContainer.set(bus.event as T)
                    MutableLiveData<T>().apply {
                        observe(this@receiveEventLive) { event ->
                            // 确保只处理最新的事件
                            if (event == latestEventContainer.get()) {
                                coroutineScope.launch { block(event) }
                            }
                        }
                    }
                } else {
                    // 原有行为：每个事件创建新的LiveData
                    MutableLiveData<T>().apply {
                        observe(this@receiveEventLive) { coroutineScope.launch { block(it) } }
                    }
                }
                // 设置LiveData的值
                liveData.value = bus.event as T
        }
    }
}


/**
 * 接收事件，不绑定生命周期
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
inline fun <reified T> receiveEventHandler(
    vararg tags: String? = arrayOf(),
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        eventFlow
            .filter { bus -> bus.event is T && (tags.isEmpty() || tags.contains(bus.tag)) }
            .collect { bus -> block(bus.event as T) }
    }
}


/**
 * 接收标签
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param block 接收到事件后执行函数，参数为标签字符串
 * @return 返回Job对象，可用于手动取消
 */
fun LifecycleOwner.receiveTag(
    vararg tags: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        eventFlow.collect { bus ->
            if (bus.event is FlowTag && !bus.tag.isNullOrBlank() && tags.contains(bus.tag)) {
                block(bus.tag)
            }
        }
    }
}

/**
 * 使用LiveData将标签消息延迟到前台接收
 * 确保标签处理在UI可见时进行
 *
 * @param tags 可接受的标签数组
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param block 接收到标签后执行函数，参数为标签字符串
 * @return 返回Job对象，可用于手动取消
 */
fun LifecycleOwner.receiveTagLive(
    vararg tags: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        eventFlow.collect { bus ->
            if (bus.event is FlowTag && !bus.tag.isNullOrBlank() && tags.contains(bus.tag)) {
                val liveData = MutableLiveData<String>()
                liveData.observe(this@receiveTagLive) { coroutineScope.launch { block(it) } }
                liveData.value = bus.tag
            }
        }
    }
}

/**
 * 接收标签，不绑定生命周期
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param block 接收到标签后执行函数，参数为标签字符串
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
fun receiveTagHandler(
    vararg tags: String?,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        eventFlow.collect { bus ->
            if (bus.event is FlowTag && !bus.tag.isNullOrEmpty() && tags.contains(bus.tag)) {
                block(bus.tag)
            }
        }
    }
}


