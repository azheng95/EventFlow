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
