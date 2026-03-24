package com.azheng.event.flow

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 事件总线实现
 * 基于Kotlin Flow实现的事件总线
 * 用于在应用程序的不同组件间传递事件，采用发布-订阅模式
 *
 * 所有函数均支持可选的 [bus] 参数：
 * - 不传 bus（默认）：使用全局默认实例 [FlowEventBus.default]
 * - 传入独立 bus：使用通过 [FlowEventBus.create] 创建的独立实例，与全局及其他实例完全隔离
 */

/**
 * 内部事件发送函数
 */
private suspend fun <T> emitEvent(event: T, tag: String?, bus: FlowEventBus) {
    bus.emitEvent(event, tag)
}

// ==================== 发送事件相关函数 ====================

/**
 * 生命周期感知的事件发送函数
 * 将事件的生命周期与组件(如Activity或Fragment)的生命周期绑定
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY，表示组件销毁时取消事件
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @return 返回Job对象，可用于手动取消
 */
fun <T> LifecycleOwner.sendEvent(
    event: T,
    tag: String? = null,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    bus: FlowEventBus = FlowEventBus.default
): Job = FlowScope(this, lifeEvent).launch {
    emitEvent(event, tag, bus)
}

/**
 * 非生命周期感知的事件发送函数
 * 可在任何协程作用域中发送事件
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 * @param scope 协程作用域，默认使用FlowScope
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @return 返回Job对象，可用于手动取消
 */
fun <T> sendEvent(
    event: T,
    tag: String? = null,
    scope: CoroutineScope = FlowScope(),
    bus: FlowEventBus = FlowEventBus.default
): Job = scope.launch {
    emitEvent(event, tag, bus)
}

/**
 * 挂起函数版本的事件发送
 * 适用于已在协程中的调用场景
 *
 * @param event 要发送的事件对象
 * @param tag 可选的事件标签
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 */
suspend fun <T> sendEventSuspend(
    event: T,
    tag: String? = null,
    bus: FlowEventBus = FlowEventBus.default
) {
    emitEvent(event, tag, bus)
}

// ==================== 发送标签相关函数 ====================

/**
 * 生命周期感知的标签发送函数
 * 发送一个只包含标签的特殊事件(FlowTag)
 *
 * @param tag 要发送的标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @return 返回Job对象，可用于手动取消
 */
fun LifecycleOwner.sendTag(
    tag: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    bus: FlowEventBus = FlowEventBus.default
): Job = sendEvent(FlowTag, tag, lifeEvent, bus)

/**
 * 非生命周期感知的标签发送函数
 *
 * @param tag 要发送的标签
 * @param scope 协程作用域，默认使用FlowScope
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @return 返回Job对象，可用于手动取消
 */
fun sendTag(
    tag: String?,
    scope: CoroutineScope = FlowScope(),
    bus: FlowEventBus = FlowEventBus.default
): Job = sendEvent(FlowTag, tag, scope, bus)

/**
 * 挂起函数版本的标签发送
 *
 * @param tag 要发送的标签
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 */
suspend fun sendTagSuspend(
    tag: String?,
    bus: FlowEventBus = FlowEventBus.default
) = sendEventSuspend(FlowTag, tag, bus)

// ==================== 接收事件相关函数 ====================

/**
 * 接收事件
 * 根据事件类型和标签过滤接收特定事件
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> LifecycleOwner.receiveEvent(
    vararg tags: String? = emptyArray(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    bus: FlowEventBus = FlowEventBus.default,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        bus.eventFlow
            .filter { busEvent -> busEvent.event is T && (tags.isEmpty() || tags.contains(busEvent.tag)) }
            .collect { busEvent -> block(busEvent.event as T) }
    }
}

/**
 * 内部实现函数 - receiveEventLive 的非内联实现
 * 将复杂逻辑移到此函数中，避免内联时的编译器错误
 *
 * 使用 Channel + repeatOnLifecycle 替代 LiveData + Queue 方案：
 * - Channel 天然支持 FIFO 保序、背压处理，消除了 LiveData.postValue 合并导致的事件丢失风险
 * - repeatOnLifecycle 是 Google 官方推荐的生命周期感知 Flow 收集方式
 * - 生产者（collect SharedFlow → Channel）始终活跃，不受前后台切换影响
 * - 消费者（for 循环从 Channel 取数据）只在前台（STARTED+）运行
 * - Channel 是破坏性读取（消费即移除），回到前台不会重复消费已处理的事件
 *
 * @param T 要接收的事件类型
 * @param eventClass 事件类型的Class对象，用于运行时类型检查
 * @param tags 标签数组
 * @param lifeEvent 生命周期事件
 * @param onlyReceiveLatest 是否只接收最新事件
 * @param serialProcessing 是否串行处理事件，默认为true（串行处理，保证顺序）
 * @param bus 事件总线实例
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
    bus: FlowEventBus,
    block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    val lifecycleOwner = this
    val targetEventFlow = bus.eventFlow

    return if (onlyReceiveLatest) {
        // ========== 只接收最新事件的模式 ==========
        // Channel.CONFLATED：缓冲区大小为1，新值自动覆盖旧值
        // 语义完美匹配 onlyReceiveLatest 需求：
        // - 前台活跃时：事件实时消费，不存在覆盖
        // - 后台期间：多个事件到来时只保留最后一个
        // - 回到前台时：只处理最后一个事件
        // 无需手动版本管理（Versioned）或引用比较（===）
        val channel = Channel<T>(Channel.CONFLATED)

        coroutineScope.launch {
            // 生产者：持续收集 SharedFlow 中的事件，放入 Channel
            // 运行在 repeatOnLifecycle 外部，不受前后台切换影响
            // 对 SharedFlow 来说是"快消费者"（trySend 非阻塞，纳秒级），不影响 SharedFlow 的缓冲策略
            launch {
                targetEventFlow
                    .filter { busEvent ->
                        eventClass.isInstance(busEvent.event) &&
                                (tags.isEmpty() || tags.contains(busEvent.tag))
                    }
                    .collect { busEvent ->
                        // trySend 用于 CONFLATED Channel，非阻塞，新值覆盖旧值
                        channel.trySend(busEvent.event as T)
                    }
            }

            // 消费者：只在前台（STARTED 及以上状态）消费事件
            // repeatOnLifecycle 行为：
            // - 进入 STARTED → 启动 block（for 循环开始消费）
            // - 降到 STOPPED → 取消 block（for 循环中断）
            // - 再次 STARTED → 重启 block（for 循环重新启动）
            // Channel 是破坏性读取，已消费的事件不会因重启而重复消费
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (event in channel) {
                    block(event)
                }
            }
        }
    } else {
        // ========== 接收所有事件的模式 ==========
        // Channel.UNLIMITED：无界缓冲，保证不丢任何事件
        // 相比原 LiveData + ConcurrentLinkedQueue + AtomicInteger + Mutex 方案：
        // - 消除了 LiveData.postValue 合并导致的触发丢失风险
        // - 消除了 pendingCount 与 queue 之间的竞态条件
        // - Channel 天然 FIFO 保序，串行模式下无需 Mutex
        // - 一个 Channel 替代四个同步原语，代码更简洁可靠
        val channel = Channel<T>(Channel.UNLIMITED)

        coroutineScope.launch {
            // 生产者：持续收集 SharedFlow 中的事件，放入 Channel
            // 运行在 repeatOnLifecycle 外部，不受前后台切换影响
            // Channel.UNLIMITED 的 send 不会挂起，对 SharedFlow 来说是"快消费者"
            launch {
                targetEventFlow
                    .filter { busEvent ->
                        eventClass.isInstance(busEvent.event) &&
                                (tags.isEmpty() || tags.contains(busEvent.tag))
                    }
                    .collect { busEvent ->
                        channel.send(busEvent.event as T)
                    }
            }

            // 消费者：只在前台（STARTED 及以上状态）消费事件
            // 后台期间事件缓存在 Channel 中，回到前台后按 FIFO 顺序逐个处理
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (event in channel) {
                    if (serialProcessing) {
                        // 串行处理模式（默认）：for 循环天然串行，保证事件按顺序处理
                        // 无需 Mutex，Channel 的 FIFO 语义已保证顺序
                        block(event)
                    } else {
                        // 并发处理模式：每个事件启动独立协程，性能更好但不保证处理顺序
                        launch { block(event) }
                    }
                }
            }
        }
    }
}

/**
 * 使用 Channel + repeatOnLifecycle 将消息延迟到前台接收
 * 确保事件处理在UI可见时进行，避免在后台处理事件
 *
 * 实现原理：
 * - 生产者持续收集 SharedFlow 事件并放入 Channel，不受前后台切换影响
 * - 消费者通过 repeatOnLifecycle 只在前台（STARTED+）从 Channel 取数据处理
 * - Channel 是破坏性读取（消费即移除），不会因前后台切换导致重复消费
 *
 * 处理模式说明：
 * - onlyReceiveLatest = true: 使用 Channel.CONFLATED，只保留最新事件，回到前台只处理最后一个
 * - onlyReceiveLatest = false: 使用 Channel.UNLIMITED，无界缓冲，保证所有事件都能被接收
 *   - serialProcessing = true（默认）: for 循环天然串行，保证事件按顺序处理
 *   - serialProcessing = false: 每个事件启动独立协程，并发处理，性能更好但不保证顺序
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签，如果标签为零个则匹配事件对象即可成功接收，如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false
 * @param serialProcessing 是否串行处理事件（for循环天然保证顺序），默认为true（串行处理，保证顺序）
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，可用于手动取消
 */
inline fun <reified T> LifecycleOwner.receiveEventLive(
    vararg tags: String? = emptyArray(),
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    onlyReceiveLatest: Boolean = false,
    serialProcessing: Boolean = true,
    bus: FlowEventBus = FlowEventBus.default,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job = receiveEventLiveImpl(T::class.java, tags, lifeEvent, onlyReceiveLatest, serialProcessing, bus, block)

/**
 * 接收事件，不绑定生命周期
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param T 要接收的事件类型
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配事件对象即可成功接收, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到事件后执行函数
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> receiveEventHandler(
    vararg tags: String? = emptyArray(),
    bus: FlowEventBus = FlowEventBus.default,
    noinline block: suspend CoroutineScope.(event: T) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        bus.eventFlow
            .filter { busEvent -> busEvent.event is T && (tags.isEmpty() || tags.contains(busEvent.tag)) }
            .collect { busEvent -> block(busEvent.event as T) }
    }
}

// ==================== 接收标签相关函数 ====================

/**
 * 接收标签
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配所有FlowTag事件, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到事件后执行函数，参数为标签字符串
 * @return 返回Job对象，可用于手动取消
 */
fun LifecycleOwner.receiveTag(
    vararg tags: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    bus: FlowEventBus = FlowEventBus.default,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    return coroutineScope.launch {
        bus.eventFlow
            .filter { busEvent ->
                busEvent.event === FlowTag &&
                        !busEvent.tag.isNullOrBlank() &&
                        (tags.isEmpty() || tags.contains(busEvent.tag))
            }
            .collect { busEvent ->
                busEvent.tag?.let { block(it) }
            }
    }
}

/**
 * 内部实现函数 - receiveTagLive 的非内联实现
 * 将复杂逻辑移到此函数中，避免内联时的编译器错误
 *
 * 使用 Channel + repeatOnLifecycle 替代 LiveData + Queue 方案：
 * - Channel 天然支持 FIFO 保序、背压处理，消除了 LiveData.postValue 合并导致的标签丢失风险
 * - repeatOnLifecycle 是 Google 官方推荐的生命周期感知 Flow 收集方式
 * - 生产者（collect SharedFlow → Channel）始终活跃，不受前后台切换影响
 * - 消费者（for 循环从 Channel 取数据）只在前台（STARTED+）运行
 * - Channel 是破坏性读取（消费即移除），回到前台不会重复消费已处理的标签
 *
 * @param tags 标签数组
 * @param lifeEvent 生命周期事件
 * @param onlyReceiveLatest 是否只接收最新标签
 * @param serialProcessing 是否串行处理标签，默认为true（串行处理，保证顺序）
 * @param bus 事件总线实例
 * @param block 接收到标签后执行函数
 * @return 返回Job对象，可用于手动取消
 */
@PublishedApi
internal fun LifecycleOwner.receiveTagLiveImpl(
    tags: Array<out String?>,
    lifeEvent: Lifecycle.Event,
    onlyReceiveLatest: Boolean,
    serialProcessing: Boolean,
    bus: FlowEventBus,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope(this, lifeEvent)
    val lifecycleOwner = this
    val targetEventFlow = bus.eventFlow

    return if (onlyReceiveLatest) {
        // ========== 只接收最新标签的模式 ==========
        // Channel.CONFLATED：缓冲区大小为1，新值自动覆盖旧值
        // 语义完美匹配 onlyReceiveLatest 需求：
        // - 前台活跃时：标签实时消费，不存在覆盖
        // - 后台期间：多个标签到来时只保留最后一个
        // - 回到前台时：只处理最后一个标签
        // 无需手动版本管理（Versioned）或引用比较（===）
        val channel = Channel<String>(Channel.CONFLATED)

        coroutineScope.launch {
            // 生产者：持续收集 SharedFlow 中的标签事件，放入 Channel
            // 运行在 repeatOnLifecycle 外部，不受前后台切换影响
            // 对 SharedFlow 来说是"快消费者"（trySend 非阻塞，纳秒级），不影响 SharedFlow 的缓冲策略
            launch {
                targetEventFlow
                    .filter { busEvent ->
                        busEvent.event === FlowTag &&
                                !busEvent.tag.isNullOrBlank() &&
                                (tags.isEmpty() || tags.contains(busEvent.tag))
                    }
                    .collect { busEvent ->
                        busEvent.tag?.let {
                            // trySend 用于 CONFLATED Channel，非阻塞，新值覆盖旧值
                            channel.trySend(it)
                        }
                    }
            }

            // 消费者：只在前台（STARTED 及以上状态）消费标签
            // repeatOnLifecycle 行为：
            // - 进入 STARTED → 启动 block（for 循环开始消费）
            // - 降到 STOPPED → 取消 block（for 循环中断）
            // - 再次 STARTED → 重启 block（for 循环重新启动）
            // Channel 是破坏性读取，已消费的标签不会因重启而重复消费
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (tag in channel) {
                    block(tag)
                }
            }
        }
    } else {
        // ========== 接收所有标签的模式 ==========
        // Channel.UNLIMITED：无界缓冲，保证不丢任何标签
        // 相比原 LiveData + ConcurrentLinkedQueue + AtomicInteger + Mutex 方案：
        // - 消除了 LiveData.postValue 合并导致的触发丢失风险
        // - 消除了 pendingCount 与 queue 之间的竞态条件
        // - Channel 天然 FIFO 保序，串行模式下无需 Mutex
        // - 一个 Channel 替代四个同步原语，代码更简洁可靠
        val channel = Channel<String>(Channel.UNLIMITED)

        coroutineScope.launch {
            // 生产者：持续收集 SharedFlow 中的标签事件，放入 Channel
            // 运行在 repeatOnLifecycle 外部，不受前后台切换影响
            // Channel.UNLIMITED 的 send 不会挂起，对 SharedFlow 来说是"快消费者"
            launch {
                targetEventFlow
                    .filter { busEvent ->
                        busEvent.event === FlowTag &&
                                !busEvent.tag.isNullOrBlank() &&
                                (tags.isEmpty() || tags.contains(busEvent.tag))
                    }
                    .collect { busEvent ->
                        busEvent.tag?.let { channel.send(it) }
                    }
            }

            // 消费者：只在前台（STARTED 及以上状态）消费标签
            // 后台期间标签缓存在 Channel 中，回到前台后按 FIFO 顺序逐个处理
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (tag in channel) {
                    if (serialProcessing) {
                        // 串行处理模式（默认）：for 循环天然串行，保证标签按顺序处理
                        // 无需 Mutex，Channel 的 FIFO 语义已保证顺序
                        block(tag)
                    } else {
                        // 并发处理模式：每个标签启动独立协程，性能更好但不保证处理顺序
                        launch { block(tag) }
                    }
                }
            }
        }
    }
}

/**
 * 使用 Channel + repeatOnLifecycle 将标签消息延迟到前台接收
 * 确保标签处理在UI可见时进行
 *
 * 实现原理：
 * - 生产者持续收集 SharedFlow 标签事件并放入 Channel，不受前后台切换影响
 * - 消费者通过 repeatOnLifecycle 只在前台（STARTED+）从 Channel 取数据处理
 * - Channel 是破坏性读取（消费即移除），不会因前后台切换导致重复消费
 *
 * 处理模式说明：
 * - onlyReceiveLatest = true: 使用 Channel.CONFLATED，只保留最新标签，回到前台只处理最后一个
 * - onlyReceiveLatest = false: 使用 Channel.UNLIMITED，无界缓冲，保证所有标签都能被接收
 *   - serialProcessing = true（默认）: for 循环天然串行，保证标签按顺序处理
 *   - serialProcessing = false: 每个标签启动独立协程，并发处理，性能更好但不保证顺序
 *
 * @param tags 要监听的标签数组，为空时接收所有标签
 * @param lifeEvent 生命周期事件，默认为ON_DESTROY时停止监听
 * @param onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false
 * @param serialProcessing 是否串行处理标签（for循环天然保证顺序），默认为true（串行处理，保证顺序）
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到标签时执行的挂起函数
 * @return 返回可用于取消监听的Job对象
 */
fun LifecycleOwner.receiveTagLive(
    vararg tags: String?,
    lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
    onlyReceiveLatest: Boolean = false,
    serialProcessing: Boolean = true,
    bus: FlowEventBus = FlowEventBus.default,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job = receiveTagLiveImpl(tags, lifeEvent, onlyReceiveLatest, serialProcessing, bus, block)

/**
 * 接收标签，不绑定生命周期
 * 和[receiveEvent]不同之处在于该函数仅支持标签, 不支持事件+标签
 * 此事件要求执行[kotlinx.coroutines.cancel]手动注销，否则可能导致内存泄漏
 *
 * @param tags 可接受零个或多个标签, 如果标签为零个则匹配所有FlowTag事件, 如果为多个则要求至少匹配一个标签才能成功接收到事件
 * @param bus 事件总线实例，默认使用全局实例，可传入独立实例实现隔离
 * @param block 接收到标签后执行函数，参数为标签字符串
 * @return 返回Job对象，必须手动取消以避免内存泄漏
 */
fun receiveTagHandler(
    vararg tags: String?,
    bus: FlowEventBus = FlowEventBus.default,
    block: suspend CoroutineScope.(tag: String) -> Unit
): Job {
    val coroutineScope = FlowScope()
    return coroutineScope.launch {
        bus.eventFlow
            .filter { busEvent ->
                busEvent.event === FlowTag &&
                        !busEvent.tag.isNullOrBlank() &&
                        (tags.isEmpty() || tags.contains(busEvent.tag))
            }
            .collect { busEvent ->
                busEvent.tag?.let { block(it) }
            }
    }
}
