package com.azheng.event.flow

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * 异步协程作用域
 *
 * 为事件总线提供协程上下文，管理协程的生命周期
 * 使用 SupervisorJob 确保一个协程的失败不会影响其他协程
 *
 * 调度策略说明：
 * - 无参 FlowScope()：默认使用 Dispatchers.Default
 *   适用于非生命周期绑定的通用事件发送/接收，避免默认占用主线程
 * - FlowScope(lifecycleOwner, lifeEvent)：默认使用 Dispatchers.Main.immediate
 *   适用于绑定 Android 生命周期的场景，便于直接进行 UI 相关处理
 */
@PublishedApi
internal open class FlowScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CoroutineScope {

    /**
     * 协程上下文配置
     * - dispatcher:
     *   - 无参构造时默认使用 Dispatchers.Default，适合通用异步任务
     *   - 生命周期构造时默认使用 Dispatchers.Main.immediate，适合UI相关操作
     * - SupervisorJob: 提供父子协程间的隔离，一个子协程失败不会取消其他子协程
     */
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    /**
     * 生命周期感知的构造函数
     *
     * 将协程作用域与Android组件的生命周期绑定，在指定的生命周期事件发生时自动取消协程
     *
     * 默认使用 Dispatchers.Main.immediate：
     * - 与 Android 生命周期 / LiveData / UI 操作的使用习惯一致
     * - 如果当前已经在主线程，可立即执行，减少不必要的调度切换
     *
     * @param lifecycleOwner 生命周期所有者，通常是Activity或Fragment
     * @param lifeEvent 触发协程取消的生命周期事件，默认为ON_DESTROY
     * @param dispatcher 协程调度器，默认为Dispatchers.Main.immediate
     */
    constructor(
        lifecycleOwner: LifecycleOwner,
        lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY,
        dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    ) : this(dispatcher) {
        // 添加生命周期观察者，在指定生命周期事件发生时取消协程作用域
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (lifeEvent == event) {
                    // 取消当前作用域中的所有协程
                    cancel()
                    // 移除观察者，避免内存泄漏
                    source.lifecycle.removeObserver(this)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }
}
