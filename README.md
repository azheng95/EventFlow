# EventFlow

[![](https://jitpack.io/v/azheng95/EventFlow.svg)](https://jitpack.io/#azheng95/EventFlow)

一个基于 **Kotlin SharedFlow** 的轻量级 Android 事件总线。  
支持：

- 生命周期感知
- 事件 + Tag 精准过滤
- 前台延迟消费
- 独立 Bus 隔离
- 可配置缓冲区与溢出策略
- 协程友好、线程安全

---

## 目录

- [功能特性](#功能特性)
- [核心设计](#核心设计)
- [安装](#安装)
- [快速开始](#快速开始)
- [线程调度模型](#线程调度模型)
- [API 说明](#api-说明)
- [独立 Bus 实例](#独立-bus-实例)
- [receiveEvent 与 receiveEventLive 的区别](#receiveevent-与-receiveeventlive-的区别)
- [SharedFlow 缓冲区模型](#sharedflow-缓冲区模型)
- [BufferOverflow 策略详解](#bufferoverflow-策略详解)
- [典型使用示例](#典型使用示例)
- [最佳实践](#最佳实践)
- [常见问题 FAQ](#常见问题-faq)
- [推荐配置速查表](#推荐配置速查表)

---

## 功能特性

- **基于 Kotlin SharedFlow**
  - 使用热流广播机制实现事件分发
  - 支持多订阅者同时接收

- **生命周期感知**
  - 自动绑定 `LifecycleOwner`
  - 到指定生命周期事件时自动取消订阅，避免泄漏

- **Tag 过滤**
  - 支持“事件类型 + Tag”组合过滤
  - 也支持仅发送/接收 Tag

- **前台延迟消费**
  - 借助 `LiveData`，将事件延迟到页面活跃状态再处理
  - 适合 UI 可见时才应该执行的逻辑

- **灵活配置**
  - 支持 `replaySize`
  - 支持 `extraBufferCapacity`
  - 支持 `BufferOverflow` 策略

- **线程安全**
  - 使用原子类、并发队列、协程锁等手段保证并发安全

- **多 Bus 隔离**
  - 支持全局默认 Bus
  - 支持独立 Bus 实例，彼此事件流完全隔离

---

## 核心设计

EventFlow 的核心思路：

1. 用 `SharedFlow` 作为事件总线
2. 用 `LifecycleOwner + CoroutineScope` 管理页面级订阅
3. 用 `LiveData` 将某些事件延迟到页面活跃时再处理
4. 用缓冲区和溢出策略控制高并发场景下的行为
5. 用全局默认 Bus + 独立 Bus 实现实例级隔离

### 核心对象

| 对象 | 作用 |
|---|---|
| `FlowEvent<T>` | 事件载体，封装 `event + tag` |
| `FlowEventBus` | 事件总线，内部基于 `MutableSharedFlow` |
| `FlowEventBusConfig` | 总线配置：`replaySize`、`extraBufferCapacity`、`bufferOverflow` |
| `FlowScope` | 协程作用域：无参默认 `Dispatchers.Default`；生命周期版默认 `Dispatchers.Main.immediate` |
| `FlowTag` | 纯标签事件标记，用于只发送标签不发送对象 |

### 全局默认 Bus 与独立 Bus

- **全局默认 Bus**
  - 不传 `bus` 参数时自动使用
  - 适合大多数通用事件通信场景

- **独立 Bus**
  - 通过 `FlowEventBus.create()` 创建
  - 适合模块隔离、不同缓冲策略、测试隔离等场景

---

## 安装

### 1. 添加 JitPack 仓库

在 `settings.gradle` 中加入：

```groovy
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

或者在 `settings.gradle.kts` 中加入：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 添加依赖

在 app 模块的 `build.gradle` 中加入：

```groovy
dependencies {
    implementation 'com.github.azheng95:EventFlow:Tag'
}
```

> 版本号请以 JitPack 徽章中的最新版本为准。

---

## 快速开始

### 1. 可选：在 `Application` 中初始化全局默认 Bus

> 如果你需要自定义全局默认 Bus 的配置，请务必在任何 `send/receive` 之前调用 `FlowEventBus.init(...)`。  
> 如果你不主动初始化，库会在首次使用全局 Bus 时按默认配置自动初始化。

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        FlowEventBus.init(
            FlowEventBusConfig.Builder()
                .setReplaySize(0)
                .setExtraBufferCapacity(2048)
                .setBufferOverflow(BufferOverflow.SUSPEND)
                .build()
        )
    }
}
```

### 2. 定义事件

```kotlin
data class OrderPaidEvent(
    val orderId: String,
    val amountFen: Long
)

data class UploadProgressEvent(
    val taskId: String,
    val progress: Int
)

data class LocationEvent(
    val lat: Double,
    val lng: Double
)
```

### 3. 发送事件

#### 3.1 在 `Activity/Fragment` 中发送（生命周期感知）

```kotlin
this.sendEvent(OrderPaidEvent("A1001", 19900), tag = "alipay")
```

#### 3.2 在任意协程作用域中发送

```kotlin
sendEvent(
    event = UploadProgressEvent("task_1", 45),
    tag = "upload",
    scope = viewModelScope
)
```

#### 3.3 在挂起函数中发送

```kotlin
suspend fun notifyPaid() {
    sendEventSuspend(OrderPaidEvent("A1001", 19900), tag = "pay")
}
```

#### 3.4 发送纯标签事件

```kotlin
sendTag("refresh_home")
```

### 4. 接收事件

> 下面的 `receiveEvent / receiveTag / receiveEventLive / receiveTagLive` 示例默认位于 `Activity/Fragment` 内部。

#### 4.1 按类型接收

```kotlin
this.receiveEvent<OrderPaidEvent> { event ->
    showToast("支付成功：${event.orderId}")
}
```

#### 4.2 按类型 + Tag 接收

```kotlin
this.receiveEvent<OrderPaidEvent>("alipay", "wechat") { event ->
    renderPaidResult(event)
}
```

#### 4.3 接收纯标签事件

```kotlin
this.receiveTag("refresh_home") { tag ->
    refreshHomeData()
}
```

#### 4.4 非生命周期场景接收

```kotlin
val job = receiveEventHandler<OrderPaidEvent>("pay") { event ->
    uploadPayLog(event)
}

// 不再需要时手动取消
job.cancel()
```

---

## 线程调度模型

EventFlow 会根据是否绑定生命周期，自动区分默认调度器。

### 默认调度规则

| 场景 | 默认作用域 | 默认调度器 | 典型 API |
|---|---|---|---|
| 无生命周期绑定 | `FlowScope()` | `Dispatchers.Default` | `sendEvent(...)`、`sendTag(...)`、`receiveEventHandler(...)`、`receiveTagHandler(...)` |
| 生命周期绑定 | `FlowScope(owner, lifeEvent)` | `Dispatchers.Main.immediate` | `LifecycleOwner.sendEvent(...)`、`receiveEvent(...)`、`receiveTag(...)`、`receiveEventLive(...)`、`receiveTagLive(...)` |
| 挂起发送 | 继承当前协程上下文 | 当前上下文 | `sendEventSuspend(...)`、`sendTagSuspend(...)` |

### 这意味着什么

#### 1. 生命周期相关 API 默认更适合 UI 场景

例如：

- `receiveEvent`
- `receiveTag`
- `receiveEventLive`
- `receiveTagLive`

默认运行在 `Dispatchers.Main.immediate`，适合直接更新 UI：

```kotlin
this.receiveEvent<OrderPaidEvent> { event ->
    payResultText.text = "支付成功：${event.orderId}"
}
```

#### 2. 非生命周期 Handler 默认不会占用主线程

例如：

- `receiveEventHandler`
- `receiveTagHandler`
- 顶层 `sendEvent(...)`
- 顶层 `sendTag(...)`

默认运行在 `Dispatchers.Default`：

```kotlin
val job = receiveEventHandler<OrderPaidEvent>("pay") { event ->
    uploadPayLog(event)
}
```

#### 3. 生命周期回调里有重活，请手动切线程

```kotlin
this.receiveEvent<OrderPaidEvent> { event ->
    withContext(Dispatchers.IO) {
        saveToDb(event)
    }
}
```

#### 4. 非生命周期 Handler 里如果要更新 UI，请切回主线程

```kotlin
val job = receiveEventHandler<OrderPaidEvent>("pay") { event ->
    withContext(Dispatchers.Main) {
        showToast("支付成功：${event.orderId}")
    }
}
```

---

## API 说明

### 发送类 API

| API | 生命周期绑定 | 默认调度器 | 说明 |
|---|---|---|---|
| `LifecycleOwner.sendEvent(...)` | 是 | `Dispatchers.Main.immediate` | 页面内发送，跟随生命周期取消 |
| `sendEvent(..., scope = xxx)` | 否（由 scope 决定） | 默认 `Dispatchers.Default` | 通用发送；若传入自定义 `scope`，则由该 `scope` 决定 |
| `sendEventSuspend(...)` | 否 | 继承当前协程上下文 | 已在协程中时直接发送 |
| `LifecycleOwner.sendTag(...)` | 是 | `Dispatchers.Main.immediate` | 页面内发送纯标签事件 |
| `sendTag(..., scope = xxx)` | 否（由 scope 决定） | 默认 `Dispatchers.Default` | 通用纯标签发送 |
| `sendTagSuspend(...)` | 否 | 继承当前协程上下文 | 已在协程中时发送纯标签 |

### 接收类 API

| API | 生命周期感知 | 延迟到活跃状态处理 | 需要手动取消 | 默认调度器 | 说明 |
|---|---|---|---|---|---|
| `receiveEvent<T>()` | 是 | 否 | 否 | `Dispatchers.Main.immediate` | 立即接收并处理 |
| `receiveEventLive<T>()` | 是 | 是 | 否 | `Dispatchers.Main.immediate` | 后台期间缓存，页面活跃时再处理 |
| `receiveEventHandler<T>()` | 否 | 否 | 是 | `Dispatchers.Default` | 非生命周期场景，必须手动取消 |
| `receiveTag()` | 是 | 否 | 否 | `Dispatchers.Main.immediate` | 只接收纯标签事件 |
| `receiveTagLive()` | 是 | 是 | 否 | `Dispatchers.Main.immediate` | 标签在页面活跃时处理 |
| `receiveTagHandler()` | 否 | 否 | 是 | `Dispatchers.Default` | 非生命周期标签接收，必须手动取消 |

> 所有收发 API 都支持可选的 `bus` 参数。  
> **不传 `bus` 时，默认使用全局默认 Bus。**

---

## 独立 Bus 实例

### 什么时候需要独立 Bus

独立 Bus 适合以下场景：

| 场景 | 说明 |
|---|---|
| 模块隔离 | 聊天、行情、日志各用自己的 Bus，避免事件串扰 |
| 不同策略 | 不同模块使用不同的 `BufferOverflow` 和缓冲区配置 |
| 测试隔离 | 每个测试用例使用独立 Bus，避免全局状态污染 |
| SDK / 插件内部通信 | 不影响宿主 App 的全局事件流 |

### 创建独立 Bus

```kotlin
// 默认配置
val myBus = FlowEventBus.create()

// 聊天专用 Bus
val chatBus = FlowEventBus.create(
    FlowEventBusConfig.Builder()
        .setExtraBufferCapacity(4096)
        .setBufferOverflow(BufferOverflow.SUSPEND)
        .build()
)

// 行情专用 Bus
val pricingBus = FlowEventBus.create(
    FlowEventBusConfig.Builder()
        .setReplaySize(1)
        .setExtraBufferCapacity(1024)
        .setBufferOverflow(BufferOverflow.DROP_OLDEST)
        .build()
)
```

### 推荐：集中管理独立 Bus

```kotlin
object AppBus {

    val chatBus: FlowEventBus = FlowEventBus.create(
        FlowEventBusConfig.Builder()
            .setExtraBufferCapacity(4096)
            .setBufferOverflow(BufferOverflow.SUSPEND)
            .build()
    )

    val pricingBus: FlowEventBus = FlowEventBus.create(
        FlowEventBusConfig.Builder()
            .setReplaySize(1)
            .setExtraBufferCapacity(1024)
            .setBufferOverflow(BufferOverflow.DROP_OLDEST)
            .build()
    )
}
```

### 使用独立 Bus 收发事件

```kotlin
// 发送到 chatBus
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)

// 页面内监听 chatBus
this.receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
    appendMessage("${event.from}: ${event.message}")
}

// 行情只关心最新值
this.receiveEventLive<PriceEvent>(
    onlyReceiveLatest = true,
    bus = AppBus.pricingBus
) { event ->
    updatePriceUI("${event.symbol}: $${event.price}")
}
```

### 全局默认 Bus vs 独立 Bus

| 特性 | 全局默认 Bus | 独立 Bus |
|---|---|---|
| 获取方式 | 不传 `bus` 参数时自动使用 | `FlowEventBus.create(config)` |
| 配置方式 | `FlowEventBus.init(config)` | 创建时传入配置 |
| 初始化时机 | 手动初始化，或首次使用时自动初始化 | 随时创建 |
| 是否可多个 | 否 | 是 |
| 事件隔离 | 否 | 是 |
| 适合场景 | 通用全局事件通信 | 模块隔离、测试隔离、不同策略 |

---

## receiveEvent 与 receiveEventLive 的区别

### `receiveEvent`

```kotlin
this.receiveEvent<OrderPaidEvent> { event ->
    render(event)
}
```

特点：

- 订阅后立即接收
- 默认在 `ON_DESTROY` 时取消
- 页面不在前台时，只要没有销毁，仍可能继续处理
- 默认运行在主线程
- 适合页面内即时响应、普通业务处理

### `receiveEventLive`

```kotlin
this.receiveEventLive<OrderPaidEvent>(
    onlyReceiveLatest = false,
    serialProcessing = true
) { event ->
    render(event)
}
```

特点：

- 内部仍然持续收集 Flow 事件
- 但真正执行 `block` 时，会延迟到页面活跃状态（通常可理解为前台）
- 默认运行在主线程
- 适合必须在 UI 可见时才处理的事件

### 对比总结

| 维度 | `receiveEvent` | `receiveEventLive` |
|---|---|---|
| 是否立即处理 | 是 | 否，活跃时再处理 |
| 是否适合前台 UI 消费 | 一般 | 更适合 |
| 后台期间行为 | 可能继续处理 | 先缓存，前台再处理 |
| 默认线程 | 主线程 | 主线程 |

### `onlyReceiveLatest`

当你只关心最新状态时，使用：

```kotlin
this.receiveEventLive<UploadProgressEvent>(
    "upload",
    onlyReceiveLatest = true
) { event ->
    progressBar.progress = event.progress
}
```

适合：

- 上传进度
- 下载进度
- 定位更新
- 传感器数据
- 股票/行情快照
- 当前状态同步

### `serialProcessing`

仅在 `onlyReceiveLatest = false` 时有意义。

#### `serialProcessing = true`（默认）

- 使用 `Mutex`
- 按顺序处理
- 适合顺序敏感场景

例如：

- 聊天消息
- 订单状态流转
- 日志写入
- 队列型任务

#### `serialProcessing = false`

- 每个事件独立协程处理
- 吞吐更高
- 不保证顺序

例如：

- 多个独立 UI 刷新
- 埋点上报
- 相互无依赖的轻量处理

> 注意：`serialProcessing = false` 不等于自动切到后台线程。  
> 生命周期相关 API 默认仍运行在主线程。  
> 如果 `block` 很重，请自行 `withContext(Dispatchers.IO/Default)`。

---

## SharedFlow 缓冲区模型

底层核心配置来自：

```kotlin
MutableSharedFlow(
    replay = config.replaySize,
    extraBufferCapacity = config.extraBufferCapacity,
    onBufferOverflow = config.bufferOverflow
)
```

### 1. `replaySize`

给**新订阅者**重放的历史事件数量。

- `0`：不保留历史
- `1`：新订阅者立刻拿到最近一条
- `N`：新订阅者拿到最近 N 条

### 2. `extraBufferCapacity`

额外缓冲区容量，主要用于应对：

- 短时间高频发送
- 收集端来不及消费
- 降低发送端阻塞概率

### 3. `bufferOverflow`

当缓冲区满时，如何处理新到来的事件。

---

## BufferOverflow 策略详解

### 策略对比

| 策略 | 含义 | 优点 | 缺点 | 适合场景 |
|---|---|---|---|---|
| `SUSPEND` | 缓冲区满时挂起发送者 | 尽量不丢事件 | 发送方可能等待 | 支付、订单、IM、关键通知 |
| `DROP_OLDEST` | 丢弃最旧事件 | 尽量保留最新状态 | 历史事件会丢 | 进度、定位、传感器、行情 |
| `DROP_LATEST` | 丢弃最新事件 | 保护旧数据 | 新事件被忽略 | 极少数保旧弃新场景 |

### 重要说明

> `SUSPEND` 不等于“未来订阅者一定能收到历史消息”。

如果：

- 当前没有订阅者
- `replaySize = 0`

那么事件并不会为未来订阅者保留。

也就是说：

- 想让后来的订阅者收到最近值，靠的是 `replaySize`
- 不是 `SUSPEND`

---

## 典型使用示例

### 1. 支付成功：不能丢

```kotlin
FlowEventBus.init(
    FlowEventBusConfig.Builder()
        .setReplaySize(0)
        .setExtraBufferCapacity(2048)
        .setBufferOverflow(BufferOverflow.SUSPEND)
        .build()
)

sendEvent(OrderPaidEvent("A1001", 19900), tag = "pay")

this.receiveEvent<OrderPaidEvent>("pay") { event ->
    showToast("支付成功：${event.orderId}")
}
```

### 2. 上传进度：只关心最新值

```kotlin
this.receiveEventLive<UploadProgressEvent>(
    "upload",
    onlyReceiveLatest = true
) { event ->
    progressBar.progress = event.progress
}
```

### 3. 聊天消息：后台缓存，前台顺序处理

```kotlin
this.receiveEventLive<ChatMessageEvent>(
    "chat_room_1",
    onlyReceiveLatest = false,
    serialProcessing = true
) { event ->
    appendMessage(event)
}
```

### 4. 纯标签事件：刷新页面

```kotlin
sendTag("refresh_home")

this.receiveTag("refresh_home") {
    refreshHomeData()
}
```

> `receiveTag / receiveTagLive` 会过滤掉 `null`、空字符串和空白字符串。

### 5. 非生命周期接收：记得手动取消

```kotlin
private var eventJob: Job? = null

fun startListen() {
    eventJob = receiveEventHandler<OrderPaidEvent>("pay") { event ->
        uploadPayLog(event)
    }
}

fun stopListen() {
    eventJob?.cancel()
}
```

### 6. 独立 Bus：聊天和行情隔离

```kotlin
object AppBus {
    val chatBus = FlowEventBus.create(
        FlowEventBusConfig.Builder()
            .setExtraBufferCapacity(4096)
            .setBufferOverflow(BufferOverflow.SUSPEND)
            .build()
    )

    val pricingBus = FlowEventBus.create(
        FlowEventBusConfig.Builder()
            .setReplaySize(1)
            .setExtraBufferCapacity(1024)
            .setBufferOverflow(BufferOverflow.DROP_OLDEST)
            .build()
    )
}

data class ChatEvent(val from: String, val message: String)
data class PriceEvent(val symbol: String, val price: Double)

// 发送到聊天 Bus
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)

// 接收聊天消息
this.receiveEventLive<ChatEvent>(
    onlyReceiveLatest = false,
    serialProcessing = true,
    bus = AppBus.chatBus
) { event ->
    appendMessage("${event.from}: ${event.message}")
}

// 行情只取最新值
this.receiveEventLive<PriceEvent>(
    onlyReceiveLatest = true,
    bus = AppBus.pricingBus
) { event ->
    updatePriceDisplay("${event.symbol}: $${event.price}")
}
```

---

## 最佳实践

### 1. 如果要自定义全局默认 Bus，请尽早初始化

放在 `Application.onCreate()` 中最稳妥。

### 2. 关键业务优先选择 `SUSPEND`

例如：

- 支付结果
- 订单状态
- 聊天消息
- 状态机流转

### 3. 高频状态流优先选择 `DROP_OLDEST`

建议搭配：

- `replaySize = 1`
- `onlyReceiveLatest = true`

### 4. 一次性事件不要轻易设置 `replay > 0`

否则新订阅者可能收到旧事件，例如：

- Toast
- 导航事件
- 支付弹窗
- 一次性提醒

### 5. 生命周期相关接收默认在主线程，重活请切线程

```kotlin
this.receiveEvent<OrderPaidEvent> { event ->
    withContext(Dispatchers.IO) {
        saveToDb(event)
    }
}
```

### 6. `receiveEventHandler / receiveTagHandler` 必须手动取消

否则可能造成内存泄漏。

### 7. `lifeEvent = ON_STOP` 要谨慎

这不是“暂停接收”，而是**直接取消订阅**。  
如果你希望“后台不处理、前台再处理”，优先使用 `receiveEventLive`。

### 8. 注意 `receiveEventLive` 的缓存增长

当 `onlyReceiveLatest = false` 时，后台期间会缓存所有事件。  
如果事件量很大，应考虑：

- 改成 `onlyReceiveLatest = true`
- 做业务限流
- 缩小事件粒度
- 使用 `DROP_OLDEST`

### 9. 独立 Bus 建议集中管理

建议使用 `object AppBus` 统一维护，便于查找和维护。

### 10. 发送和接收必须使用同一个 Bus

```kotlin
// ❌ 发到 chatBus，但在全局默认 Bus 上接收
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)
this.receiveEvent<ChatEvent> { /* 收不到 */ }

// ✅ 发送和接收都使用 chatBus
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)
this.receiveEvent<ChatEvent>(bus = AppBus.chatBus) { /* 能收到 */ }
```

---

## 常见问题 FAQ

### Q1：为什么我的自定义配置不生效？

因为全局默认 Bus 可能已经在你调用 `init(...)` 之前自动初始化了。

解决方式：

- 如果要自定义全局配置，请在 `Application.onCreate()` 中尽早调用 `FlowEventBus.init(...)`
- 确保在任何 `send / receive` 之前完成初始化

---

### Q2：为什么后来订阅的人收不到之前发送的事件？

因为默认 `replaySize = 0`。  
`SharedFlow` 不是消息队列，不会默认替未来订阅者保存历史值。

如果你需要“后来的订阅者拿到最近值”，请设置：

```kotlin
setReplaySize(1)
```

---

### Q3：为什么设置了 `SUSPEND`，后来的人还是收不到历史消息？

因为 `SUSPEND` 解决的是“缓冲区满时怎么处理”，不是“替未来订阅者保存历史”。

能否重放历史，取决于：

```kotlin
replaySize
```

---

### Q4：为什么 `receiveTag()` 没反应？

请检查：

- tag 是否为 `null`
- tag 是否为空字符串或空白字符串
- 监听 tag 是否匹配
- 发送和接收是否使用了同一个 Bus

---

### Q5：为什么我设置了 `serialProcessing = false`，还是会卡 UI？

因为它只表示“不串行处理”，不表示“自动切后台线程”。

如果你使用的是生命周期相关 API：

- `receiveEvent`
- `receiveTag`
- `receiveEventLive`
- `receiveTagLive`

它们默认仍运行在主线程。

正确做法：

```kotlin
this.receiveEventLive<HeavyEvent>(
    onlyReceiveLatest = false,
    serialProcessing = false
) { event ->
    withContext(Dispatchers.Default) {
        doHeavyWork(event)
    }
}
```

---

### Q6：什么时候用 `receiveEvent`，什么时候用 `receiveEventLive`？

- **业务可以立即处理**：用 `receiveEvent`
- **必须等页面活跃再处理**：用 `receiveEventLive`

可以简单理解为：

- `receiveEvent` = 直接消费
- `receiveEventLive` = 先收着，活跃时再处理

---

### Q7：什么时候该用独立 Bus？

| 情况 | 建议 |
|---|---|
| 简单全局事件通信 | 用全局默认 Bus 即可 |
| 不同模块需要不同 BufferOverflow 策略 | 用独立 Bus |
| 要避免模块间事件串扰 | 用独立 Bus |
| SDK / 插件内部通信不想影响宿主 App | 用独立 Bus |
| 单元测试 / 集成测试 | 用独立 Bus |

---

### Q8：独立 Bus 需要在 `Application` 中初始化吗？

不需要。

独立 Bus 通过：

```kotlin
FlowEventBus.create(config)
```

直接创建，配置在创建时就已确定。  
只有全局默认 Bus 才涉及 `FlowEventBus.init(config)`。

---

### Q9：为什么 `receiveEventHandler()` 里直接更新 UI 会报线程问题？

因为它默认运行在 `Dispatchers.Default`，不是主线程。

如果要更新 UI，请切回主线程：

```kotlin
val job = receiveEventHandler<OrderPaidEvent>("pay") { event ->
    withContext(Dispatchers.Main) {
        showToast("支付成功：${event.orderId}")
    }
}
```

---

## 推荐配置速查表

### 全局默认 Bus 推荐配置

| 场景 | replaySize | extraBufferCapacity | BufferOverflow | 推荐接收方式 |
|---|---:|---:|---|---|
| 支付结果 | 0 | 2048 | `SUSPEND` | `receiveEvent` |
| 订单状态流转 | 0 | 2048 | `SUSPEND` | `receiveEventLive(false, true)` |
| 聊天消息 | 0 | 2048 | `SUSPEND` | `receiveEventLive(false, true)` |
| 上传进度 | 1 | 128 | `DROP_OLDEST` | `receiveEventLive(true)` |
| 定位更新 | 1 | 64 | `DROP_OLDEST` | `receiveEventLive(true)` |
| 股票/行情快照 | 1 | 256 | `DROP_OLDEST` | `receiveEventLive(true)` |
| 限流触发任务 | 0 | 128 | `DROP_LATEST` | 视业务而定 |

### 独立 Bus 推荐配置

| Bus 用途 | replaySize | extraBufferCapacity | BufferOverflow | 说明 |
|---|---:|---:|---|---|
| 聊天模块 | 0 | 4096 | `SUSPEND` | 保证消息不丢失 |
| 行情模块 | 1 | 1024 | `DROP_OLDEST` | 只关心最新价格 |
| 日志模块 | 0 | 2048 | `SUSPEND` | 保证日志完整 |
| 传感器模块 | 1 | 64 | `DROP_OLDEST` | 只保留最新采样 |
| 测试专用 | 0 | 256 | `SUSPEND` | 简单稳定即可 |

---

## 总结

EventFlow 的定位非常明确：

- **关键业务不丢**：`SUSPEND`
- **高频状态保最新**：`DROP_OLDEST + onlyReceiveLatest = true`
- **模块隔离**：`FlowEventBus.create()`
- **页面 UI 场景**：优先使用生命周期相关接收 API
- **通用后台处理**：优先使用非生命周期 Handler
- **默认不传 `bus`**：就是全局默认 Bus

如果你希望在 Android 项目里拥有一个：

- 简单
- 协程友好
- 生命周期安全
- 可控缓冲策略
- 支持模块隔离

的事件总线，那么 EventFlow 就是一个很实用的选择。
```
