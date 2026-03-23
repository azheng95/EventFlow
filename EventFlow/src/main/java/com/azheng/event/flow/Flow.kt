package com.azheng.event.flow

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
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

/**
 * 内部事件发送函数
 */
private suspend fun <T> emitEvent(event: T, tag: String?) {
    FlowEventBus.emitEvent(event, tag)
}

// ==================== 发送事件相关函数 ====================

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
): Job = FlowScope(this, lifeEvent).launch {
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
fun <T> sendEvent(
    event: T,
    tag: String? = null,
    scope: CoroutineScope = FlowScope()
): Job = scope.launch {
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

// ==================== 发送标签相关函数 ====================

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
): Job = sendEvent(FlowTag, tag, lifeEvent)

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
): Job = sendEvent(FlowTag, tag, scope)

/**
 * 挂起函数版本的标签发送
 *
 * @param tag 要发送的标签
 */
suspend fun sendTagSuspend(tag: String?) = sendEventSuspend(FlowTag, tag)

// ==================== 接收事件相关函数 ====================

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
@Suppress("UNCHECKED_CAST")
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
 * 内部实现函数 - receiveEventLive 的非内联实现
 * 将复杂逻辑移到此函数中，避免内联时的编译器错误
 *
 * @param T 要接收的事件类型
 * @param eventClass 事件类型的Class对象，用于运行时类型检查
 * @param tags 标签数组
 * @param lifeEvent 生命周期事件
 * @param onlyReceiveLatest 是否只接收最新事件
 * @param serialProcessing 是否串行处理事件（使用Mutex保证顺序），默认为true（串行处理，保证顺序）
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> LifecycleOwner.receiveEventLiveImpl(
    eventClass: Class<T>,
    tags: Array<out String?>,
    lifeEvent: Lifecycle.Event,
    onlyReceiveLatest: Boolean,
    serialProcessing: Boolean,
    block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    val lifecycleOwner = this

    return if (onlyReceiveLatest) {
        // ========== 只接收最新事件的模式 ==========
        // 使用 AtomicReference 保存最新事件，确保线程安全
        val latestEventContainer = AtomicReference<T?>(null)
        val liveData = MutableLiveData<T>().apply {
            observe(lifecycleOwner) { event ->
                // 只有当事件是最新的才处理，避免处理过期事件
                if (event != null && event === latestEventContainer.get()) {
                    coroutineScope.launch { block(event) }
                }
            }
        }

        coroutineScope.launch {
            eventFlow
                .filter { bus -> eventClass.isInstance(bus.event) && (tags.isEmpty() || tags.contains(bus.tag)) }
                .collect { bus ->
                    val event = bus.event as T
                    latestEventContainer.set(event)
                    liveData.postValue(event)
                }
        }
    } else {
        // ========== 接收所有事件的模式 - 队列 + 计数器双重保障 ==========
        // ConcurrentLinkedQueue 是线程安全的无界队列
        val pendingEvents = ConcurrentLinkedQueue<T>()
        // AtomicInteger 计数器用于双重保障，防止极端情况下的事件丢失
        val pendingCount = AtomicInteger(0)
        // Mutex 用于保证事件处理的顺序性，协程挂起式锁，不阻塞线程（仅在 serialProcessing=true 时使用）
        val processingMutex = if (serialProcessing) Mutex() else null

        // 使用时间戳触发LiveData更新，确保每次都能触发观察者
        val triggerLiveData = MutableLiveData<Long>().apply {
            observe(lifecycleOwner) {
                if (serialProcessing && processingMutex != null) {
                    // ========== 串行处理模式（默认）：使用 Mutex 保证顺序执行 ==========
                    coroutineScope.launch {
                        // 获取锁，保证同一时间只有一个协程在处理事件队列
                        processingMutex.withLock {
                            // 循环处理直到队列为空或计数器归零，确保所有事件都被处理
                            while (pendingCount.get() > 0) {
                                val event = pendingEvents.poll()
                                if (event != null) {
                                    // 成功取出事件后才减少计数
                                    pendingCount.decrementAndGet()
                                    // 在锁内顺序执行，保证事件处理顺序
                                    block(event)
                                } else {
                                    // 队列为空但计数器不为零，可能存在竞态条件
                                    // 退出循环，等待下次 LiveData 触发重新处理
                                    break
                                }
                            }
                        }
                    }
                } else {
                    // ========== 并发处理模式：每个事件独立协程处理，性能更好但不保证顺序 ==========
                    // 循环处理直到队列为空或计数器归零，确保所有事件都被处理
                    while (pendingCount.get() > 0) {
                        val event = pendingEvents.poll()
                        if (event != null) {
                            // 成功取出事件后才减少计数
                            pendingCount.decrementAndGet()
                            // 每个事件启动独立协程，并发执行
                            coroutineScope.launch { block(event) }
                        } else {
                            // 队列为空但计数器不为零，可能存在竞态条件
                            // 退出循环，等待下次 LiveData 触发重新处理
                            break
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            eventFlow
                .filter { bus -> eventClass.isInstance(bus.event) && (tags.isEmpty() || tags.contains(bus.tag)) }
                .collect { bus ->
                    val event = bus.event as T
                    // 先增加计数器，确保观察者能感知到有待处理事件
                    pendingCount.incrementAndGet()
                    // 再将事件加入队列
                    pendingEvents.offer(event)
                    // 使用时间戳确保 LiveData 值变化，触发观察者
                    triggerLiveData.postValue(System.nanoTime())
                }
        }
    }
}

/**
 * 使用LiveData将消息延迟到前台接收
 * 确保事件处理在UI可见时进行，避免在后台处理事件
 *
 * 处理模式说明：
 * - onlyReceiveLatest = true: 只接收最新事件，回到前台只处理最后一个
 * - onlyReceiveLatest = false: 使用队列 + AtomicInteger计数器，确保所有事件都能被接收
 *   - serialProcessing = true（默认）: 串行处理，使用Mutex互斥锁保证事件按顺序处理
 *   - serialProcessing = false: 并发处理，性能更好，但不保证处理顺序
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签，如果标签为零个则匹配事件对象即可成功接收，如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false
 * @param serialProcessing 是否串行处理事件（使用Mutex保证顺序），默认为true（串行处理，保证顺序）
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
inline fun <reified T> LifecycleOwner.receiveEventLive(
    vararg tags: String? = emptyArray(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    onlyReceiveLatest: Boolean = false,
    serialProcessing: Boolean = true,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job = receiveEventLiveImpl(T::class.java, tags, lifeEvent, onlyReceiveLatest, serialProcessing, block)

/**
 * 接收事件，不绑定生命周期
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> receiveEventHandler(
    vararg tags: String? = emptyArray(),
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        eventFlow
            .filter { bus -> bus.event is T && (tags.isEmpty() || tags.contains(bus.tag)) }
            .collect { bus -> block(bus.event as T) }
    }
}

// ==================== 接收标签相关函数 ====================

/**
 * 接收标签
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配所有FlowTag事件, 如果为多个则要求至少匹配一个标签才能成功接收到事件
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
        eventFlow
            .filter { bus ->
                bus.event === FlowTag &&
                        !bus.tag.isNullOrBlank() &&
                        (tags.isEmpty() || tags.contains(bus.tag))
            }
            .collect { bus ->
                bus.tag?.let { block(it) }
            }
    }
}

/**
 * 内部实现函数 - receiveTagLive 的非内联实现
 * 将复杂逻辑移到此函数中，避免内联时的编译器错误
 *
 * @param tags 标签数组
 * @param lifeEvent 生命周期事件
 * @param onlyReceiveLatest 是否只接收最新标签
 * @param serialProcessing 是否串行处理标签（使用Mutex保证顺序），默认为true（串行处理，保证顺序）
 * @param block 接收到标签后执行函数
 * @return 返回Job对象，可用于手动取消
 */
@PublishedApi
internal fun LifecycleOwner.receiveTagLiveImpl(
    tags: Array<out String?>,
    lifeEvent: Lifecycle.Event,
    onlyReceiveLatest: Boolean,
    serialProcessing: Boolean,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    val lifecycleOwner = this

    return if (onlyReceiveLatest) {
        // ========== 只接收最新标签的模式 ==========
        // 使用 AtomicReference 保存最新标签，确保线程安全
        val latestTagContainer = AtomicReference<String?>(null)
        val liveData = MutableLiveData<String>().apply {
            observe(lifecycleOwner) { tag ->
                // 只有当标签是最新的才处理，避免处理过期标签
                if (tag != null && tag === latestTagContainer.get()) {
                    coroutineScope.launch { block(tag) }
                }
            }
        }

        coroutineScope.launch {
            eventFlow
                .filter { bus ->
                    bus.event === FlowTag &&
                            !bus.tag.isNullOrBlank() &&
                            (tags.isEmpty() || tags.contains(bus.tag))
                }
                .collect { bus ->
                    val tag = bus.tag ?: return@collect
                    latestTagContainer.set(tag)
                    liveData.postValue(tag)
                }
        }
    } else {
        // ========== 接收所有标签的模式 - 队列 + 计数器双重保障 ==========
        // ConcurrentLinkedQueue 是线程安全的无界队列
        val pendingTags = ConcurrentLinkedQueue<String>()
        // AtomicInteger 计数器用于双重保障，防止极端情况下的标签丢失
        val pendingCount = AtomicInteger(0)
        // Mutex 用于保证标签处理的顺序性，协程挂起式锁，不阻塞线程（仅在 serialProcessing=true 时使用）
        val processingMutex = if (serialProcessing) Mutex() else null

        // 使用时间戳触发LiveData更新，确保每次都能触发观察者
        val triggerLiveData = MutableLiveData<Long>().apply {
            observe(lifecycleOwner) {
                if (serialProcessing && processingMutex != null) {
                    // ========== 串行处理模式（默认）：使用 Mutex 保证顺序执行 ==========
                    coroutineScope.launch {
                        // 获取锁，保证同一时间只有一个协程在处理标签队列
                        processingMutex.withLock {
                            // 循环处理直到队列为空或计数器归零，确保所有标签都被处理
                            while (pendingCount.get() > 0) {
                                val tag = pendingTags.poll()
                                if (tag != null) {
                                    // 成功取出标签后才减少计数
                                    pendingCount.decrementAndGet()
                                    // 在锁内顺序执行，保证标签处理顺序
                                    block(tag)
                                } else {
                                    // 队列为空但计数器不为零，可能存在竞态条件
                                    // 退出循环，等待下次 LiveData 触发重新处理
                                    break
                                }
                            }
                        }
                    }
                } else {
                    // ========== 并发处理模式：每个标签独立协程处理，性能更好但不保证顺序 ==========
                    // 循环处理直到队列为空或计数器归零，确保所有标签都被处理
                    while (pendingCount.get() > 0) {
                        val tag = pendingTags.poll()
                        if (tag != null) {
                            // 成功取出标签后才减少计数
                            pendingCount.decrementAndGet()
                            // 每个标签启动独立协程，并发执行
                            coroutineScope.launch { block(tag) }
                        } else {
                            // 队列为空但计数器不为零，可能存在竞态条件
                            // 退出循环，等待下次 LiveData 触发重新处理
                            break
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            eventFlow
                .filter { bus ->
                    bus.event === FlowTag &&
                            !bus.tag.isNullOrBlank() &&
                            (tags.isEmpty() || tags.contains(bus.tag))
                }
                .collect { bus ->
                    val tag = bus.tag ?: return@collect
                    // 先增加计数器，确保观察者能感知到有待处理标签
                    pendingCount.incrementAndGet()
                    // 再将标签加入队列
                    pendingTags.offer(tag)
                    // 使用时间戳确保 LiveData 值变化，触发观察者
                    triggerLiveData.postValue(System.nanoTime())
                }
        }
    }
}

/**
 * 使用LiveData将标签消息延迟到前台接收
 * 确保标签处理在UI可见时进行
 *
 * 处理模式说明：
 * - onlyReceiveLatest = true: 只接收最新标签，回到前台只处理最后一个
 * - onlyReceiveLatest = false: 使用队列 + AtomicInteger计数器，确保所有标签都能被接收
 *   - serialProcessing = true（默认）: 串行处理，使用Mutex互斥锁保证标签按顺序处理
 *   - serialProcessing = false: 并发处理，性能更好，但不保证处理顺序
 *
 * @param tags 要监听的标签数组，为空时接收所有标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY时停止监听
 * @param onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false
 * @param serialProcessing 是否串行处理标签（使用Mutex保证顺序），默认为true（串行处理，保证顺序）
 * @param block 接收到标签时执行的挂起函数
 * @return 返回可用于取消监听的Job对象
 */
fun LifecycleOwner.receiveTagLive(
    vararg tags: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    onlyReceiveLatest: Boolean = false,
    serialProcessing: Boolean = true,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job = receiveTagLiveImpl(tags, lifeEvent, onlyReceiveLatest, serialProcessing, block)

/**
 * 接收标签，不绑定生命周期
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配所有FlowTag事件, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param block 接收到标签后执行函数，参数为标签字符串
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
fun receiveTagHandler(
    vararg tags: String?,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        eventFlow
            .filter { bus ->
                bus.event === FlowTag &&
                        !bus.tag.isNullOrBlank() &&
                        (tags.isEmpty() || tags.contains(bus.tag))
            }
            .collect { bus ->
                bus.tag?.let { block(it) }
            }
    }
}
