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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
 * 默认在主线程调度器上执行，使用 immediate 模式减少不必要的延迟
 */
@PublishedApi
internal open class FlowScope() : CoroutineScope {
    /**
     * 协程上下文配置
     * - Dispatchers.Main.immediate: 在主线程上立即执行协程，适合UI操作
     * - SupervisorJob: 提供父子协程间的隔离，一个子协程失败不会取消其他子协程
     */
    override val coroutineContext: CoroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    /**
     * 生命周期感知的构造函数
     *
     * 将协程作用域与Android组件的生命周期绑定，在指定的生命周期事件发生时自动取消协程
     *
     * @param lifecycleOwner 生命周期所有者，通常是Activity或Fragment
     * @param lifeEvent 触发协程取消的生命周期事件，默认为ON_DESTROY
     */
    constructor(
        lifecycleOwner: LifecycleOwner,
        lifeEvent: Lifecycle.Event = Lifecycle.Event.ON_DESTROY
    ) : this() {
        // 添加生命周期观察者，在指定生命周期事件发生时取消协程作用域
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (lifeEvent == event) {
                    // 取消当前作用域中的所有协程
                    cancel()
                }
            }
        })
    }

}

