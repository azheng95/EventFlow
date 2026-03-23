[![](https://jitpack.io/v/azheng95/EventFlow.svg)](https://jitpack.io/#azheng95/EventFlow)

## 目录

- [功能特性](#功能特性)
- [核心设计](#核心设计)
- [快速开始](#快速开始)
- [API 说明](#api-说明)
- [独立 Bus 实例](#独立-bus-实例)
- [receiveEvent 与 receiveEventLive 区别](#receiveevent-与-receiveeventlive-区别)
- [SharedFlow 缓冲区模型](#sharedflow-缓冲区模型)
- [BufferOverflow 策略详解](#bufferoverflow-策略详解)
- [典型使用示例](#典型使用示例)
- [最佳实践](#最佳实践)
- [常见问题 FAQ](#常见问题-faq)
- [推荐配置速查表](#推荐配置速查表)

---

## ✨功能特性

- 🚀 **基于 Kotlin Flow** — 充分利用 Flow 的冷流和背压处理能力
- 🔄 **生命周期感知** — 自动绑定 LifecycleOwner，避免内存泄漏
- 🏷️ **标签过滤** — 支持事件 + 标签组合过滤，精准投递
- 📱 **LiveData 集成** — 支持前台延迟接收，确保 UI 可见时处理事件
- ⚙️ **灵活配置** — 支持自定义缓冲区大小和溢出策略
- 🔒 **线程安全** — 使用原子操作和协程锁保证并发安全
- 🔀 **多 Bus 隔离** — 支持全局默认 Bus 和多个独立 Bus 实例，事件流完全隔离、互不影响

---

## 核心设计

基于 Kotlin Flow 实现的轻量级、高性能事件总线框架，专为 Android 应用设计。采用发布-订阅模式，支持生命周期感知，完美配合协程使用。

### 核心对象

| 对象 | 作用 |
|---|---|
| `FlowEvent<T>` | 事件载体，封装 `event + tag` |
| `FlowEventBus` | 事件总线，内部基于 `MutableSharedFlow`；支持全局默认实例和独立实例 |
| `FlowEventBusConfig` | 总线配置：`replaySize`、`extraBufferCapacity`、`bufferOverflow` |
| `FlowScope` | 事件总线协程作用域，默认 `Dispatchers.Main.immediate + SupervisorJob()` |
| `FlowTag` | 纯标签事件标记，用于只传递 tag 不传数据的场景 |

### 架构示意

```
┌──────────────────────────────────────────────────┐
│                   Application                     │
│                                                   │
│  ┌─────────────────┐  ┌────────────────────────┐ │
│  │  全局默认 Bus     │  │   独立 Bus 实例         │ │
│  │  FlowEventBus   │  │                        │ │
│  │  .default       │  │  chatBus  pricingBus   │ │
│  │                 │  │  logBus   ...          │ │
│  │  SharedFlow ━━  │  │  SharedFlow ━━━━━━━━━  │ │
│  │  ┃ 全局事件流    │  │  ┃ 各自独立事件流       │ │
│  └─────────────────┘  └────────────────────────┘ │
│         ↕ 互不影响 ↕          ↕ 互不影响 ↕          │
└──────────────────────────────────────────────────┘
```

---

## 快速开始

### 1. 添加依赖

在 `settings.gradle` 文件中加入：

```groovy
dependencyResolutionManagement {
    repositories {
        // JitPack 远程仓库：https://jitpack.io
        maven { url 'https://jitpack.io' }
    }
}
```

或者在 `settings.gradle.kts` 文件中加入：

```kotlin
dependencyResolutionManagement {
    repositories {
        // JitPack 远程仓库：https://jitpack.io
        maven { url = uri("https://jitpack.io") }
    }
}
```

在项目 app 模块下的 `build.gradle` 文件中加入远程依赖：

```groovy
dependencies {
    implementation 'com.github.azheng95:EventFlow:Tag'
}
```

### 2. 在 `Application` 中尽早初始化

> **非常重要：一定要在任何 send/receive 之前调用 `FlowEventBus.init(...)`。**  
> 因为一旦第一次访问 `eventFlow` 或第一次发消息，就会自动用默认配置初始化。  
> 后续再自定义配置就不会生效。

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 不初始化会使用默认配置
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

### 3. 定义事件

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

### 4. 发送事件

#### 4.1 在 `Activity/Fragment` 中发送（生命周期感知）

```kotlin
sendEvent(OrderPaidEvent("A1001", 19900), tag = "alipay")
```

#### 4.2 在任意协程作用域中发送

```kotlin
sendEvent(
    event = UploadProgressEvent("task_1", 45),
    tag = "upload",
    scope = viewModelScope
)
```

#### 4.3 在挂起函数中发送

```kotlin
suspend fun notifyPaid() {
    sendEventSuspend(OrderPaidEvent("A1001", 19900), tag = "pay")
}
```

#### 4.4 发送纯标签事件

```kotlin
sendTag("refresh_home")
```

### 5. 接收事件

#### 5.1 按类型接收

```kotlin
receiveEvent<OrderPaidEvent> { event ->
    showToast("支付成功：${event.orderId}")
}
```

#### 5.2 按类型 + Tag 接收

```kotlin
receiveEvent<OrderPaidEvent>("alipay", "wechat") { event ->
    renderPaidResult(event)
}
```

**说明：**

- `T`：过滤事件类型
- `tags`：可传 0 个或多个 tag
- 不传 tag：只按类型匹配
- 传多个 tag：命中任意一个即可

---

## API 说明

### 1. 发送类 API

| API | 生命周期绑定 | 适用场景 |
|---|---|---|
| `LifecycleOwner.sendEvent(...)` | 是 | 页面内发送，跟随页面销毁 |
| `sendEvent(..., scope = xxx)` | 否（由 scope 决定） | ViewModel、全局协程、业务层 |
| `sendEventSuspend(...)` | 否 | 已在协程内部，直接发送 |
| `sendTag(...)` | 同上 | 只需要 tag，不需要事件对象 |
| `sendTagSuspend(...)` | 否 | 已在协程内部，发送纯标签 |

> 所有发送类 API 均支持可选的 `bus` 参数，默认使用全局 Bus。详见 [独立 Bus 实例](#独立-bus-实例)。

### 2. 接收类 API

| API | 生命周期感知 | 延迟到前台处理 | 需要手动取消 | 说明 |
|---|---|---|---|---|
| `receiveEvent<T>()` | 是 | 否 | 否 | 立即接收并处理 |
| `receiveEventLive<T>()` | 是 | 是 | 否 | 后台期间先缓存，前台再处理 |
| `receiveEventHandler<T>()` | 否 | 否 | **是** | 非生命周期场景，必须 `cancel()` |
| `receiveTag()` | 是 | 否 | 否 | 只接收 tag 事件 |
| `receiveTagLive()` | 是 | 是 | 否 | tag 在前台处理 |
| `receiveTagHandler()` | 否 | 否 | **是** | 非生命周期 tag 接收 |

> 所有接收类 API 均支持可选的 `bus` 参数，默认使用全局 Bus。详见 [独立 Bus 实例](#独立-bus-实例)。

---

## 独立 Bus 实例

### 设计理念

除了全局默认 Bus，还可以通过 `FlowEventBus.create()` 创建**完全独立的 Bus 实例**。每个实例：

- 拥有**独立的 `SharedFlow`** 事件流
- 拥有**独立的配置**（`replaySize`、`bufferOverflow` 等）
- 与全局 Bus 及其他独立实例**完全隔离，互不影响**

这在以下场景非常有用：

| 场景 | 说明 |
|---|---|
| **模块隔离** | 聊天模块、行情模块、日志模块各用独立 Bus，防止事件串扰 |
| **不同策略** | 聊天用 `SUSPEND` 保证不丢消息，行情用 `DROP_OLDEST` 只留最新价格 |
| **测试友好** | 每个测试用例创建独立 Bus，避免全局状态污染 |
| **组件解耦** | SDK / 插件内部使用独立 Bus，不与宿主 App 冲突 |

### 创建独立 Bus 实例

```kotlin
// 使用默认配置
val myBus = FlowEventBus.create()

// 使用自定义配置
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
```

### 推荐：集中管理独立 Bus

建议使用 `object` 单例集中管理所有独立 Bus 实例：

```kotlin
object AppBus {

    /** 聊天专用 Bus - SUSPEND 策略，保证消息不丢失 */
    val chatBus: FlowEventBus = FlowEventBus.create(
        FlowEventBusConfig.Builder()
            .setExtraBufferCapacity(4096)
            .setBufferOverflow(BufferOverflow.SUSPEND)
            .build()
    )

    /** 行情专用 Bus - DROP_OLDEST 策略，只关心最新价格 */
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

所有收发 API 均通过 `bus` 参数指定目标 Bus：

```kotlin
// ==================== 发送 ====================

// 发送事件到 chatBus
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)

// 挂起函数发送到 chatBus
sendEventSuspend(ChatEvent("张三", "你好"), bus = AppBus.chatBus)

// LifecycleOwner 扩展发送到 pricingBus
this.sendEvent(PriceEvent("AAPL", 185.5), bus = AppBus.pricingBus)

// 发送标签到 chatBus
sendTag("chat_typing", bus = AppBus.chatBus)

// ==================== 接收 ====================

// 接收 chatBus 上的 ChatEvent（全局 Bus 的 ChatEvent 不会触发此回调）
receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event ->
    appendMessage("${event.from}: ${event.message}")
}

// receiveEventLive 接收 pricingBus 上的行情（只关心最新价格）
receiveEventLive<PriceEvent>(
    onlyReceiveLatest = true,
    bus = AppBus.pricingBus
) { event ->
    updatePriceUI("${event.symbol}: $${event.price}")
}

// 接收 chatBus 上的标签
receiveTag("chat_typing", bus = AppBus.chatBus) { tag ->
    showTypingIndicator()
}

// receiveTagLive 接收 chatBus 上的标签（后台延迟到前台处理）
receiveTagLive(
    "chat_new_member",
    onlyReceiveLatest = false,
    bus = AppBus.chatBus
) { tag ->
    refreshMemberList()
}

// 不绑定生命周期的独立 Bus 接收（需要手动取消！）
val job = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
    logChatMessage(event)
}
// 记得取消：job.cancel()
```

### 隔离验证

```kotlin
// 向三个不同的 Bus 发送同类型事件
sendEvent(MessageEvent("全局Bus的消息"))                              // → 全局 Bus
sendEvent(MessageEvent("chatBus的消息"), bus = AppBus.chatBus)       // → chatBus
sendEvent(MessageEvent("pricingBus的消息"), bus = AppBus.pricingBus) // → pricingBus

// 接收端：各自只能收到对应 Bus 上的消息
receiveEvent<MessageEvent> { event ->
    // ✅ 只收到 "全局Bus的消息"
}

receiveEvent<MessageEvent>(bus = AppBus.chatBus) { event ->
    // ✅ 只收到 "chatBus的消息"
}

receiveEvent<MessageEvent>(bus = AppBus.pricingBus) { event ->
    // ✅ 只收到 "pricingBus的消息"
}
```

### 全局 Bus vs 独立 Bus 对比

| 特性 | 全局 Bus（默认） | 独立 Bus |
|---|---|---|
| 获取方式 | `FlowEventBus.default`（自动） | `FlowEventBus.create(config)` |
| 初始化时机 | `Application.onCreate` 或自动懒初始化 | 随时创建 |
| 配置方式 | `FlowEventBus.init(config)` | 构造时传入 config |
| 是否可创建多个 | 否，全局唯一 | 是，可创建任意多个 |
| 事件流隔离 | — | ✅ 各实例完全隔离 |
| API 使用方式 | 不传 `bus` 参数 | 传 `bus = myBus` |
| 适合场景 | 通用的全局事件通信 | 模块隔离、不同策略、测试 |

### 独立 Bus 支持的完整 API

| API | 全局 Bus | 独立 Bus |
|-----|---------|---------|
| `sendEvent` | ✅ | ✅ `bus = myBus` |
| `LifecycleOwner.sendEvent` | ✅ | ✅ `bus = myBus` |
| `sendEventSuspend` | ✅ | ✅ `bus = myBus` |
| `sendTag` | ✅ | ✅ `bus = myBus` |
| `LifecycleOwner.sendTag` | ✅ | ✅ `bus = myBus` |
| `sendTagSuspend` | ✅ | ✅ `bus = myBus` |
| `receiveEvent` | ✅ | ✅ `bus = myBus` |
| `receiveEventLive` | ✅ | ✅ `bus = myBus` |
| `receiveEventHandler` | ✅ | ✅ `bus = myBus` |
| `receiveTag` | ✅ | ✅ `bus = myBus` |
| `receiveTagLive` | ✅ | ✅ `bus = myBus` |
| `receiveTagHandler` | ✅ | ✅ `bus = myBus` |

---

## receiveEvent 与 receiveEventLive 区别

### `receiveEvent`

```kotlin
receiveEvent<OrderPaidEvent> { event ->
    render(event)
}
```

特点：

- 订阅后立即开始收
- 默认随 `ON_DESTROY` 取消
- 即使页面不在前台，只要没有销毁，仍可能继续处理
- 适合：业务逻辑、非 UI 强依赖事件

### `receiveEventLive`

```kotlin
receiveEventLive<OrderPaidEvent>(
    onlyReceiveLatest = false,
    serialProcessing = true
) { event ->
    render(event)
}
```

特点：

- 内部依然会收集 Flow 事件
- 但真正执行 `block` 会借助 `LiveData.observe(owner)`，延后到页面**活跃状态**再处理
- 适合：必须在 UI 可见/活跃时消费的事件

### `onlyReceiveLatest` 说明

#### `onlyReceiveLatest = true`

只保留最后一条，适合：

- 进度更新
- 定位更新
- 传感器数据
- 股票/行情快照
- 当前状态同步

```kotlin
receiveEventLive<UploadProgressEvent>(
    "upload",
    onlyReceiveLatest = true
) { event ->
    progressBar.progress = event.progress
}
```

#### `onlyReceiveLatest = false`

接收全部事件，适合：

- 聊天消息
- 支付结果
- 订单状态变更
- 通知中心
- 审批流

```kotlin
receiveEventLive<OrderPaidEvent>(
    "pay",
    onlyReceiveLatest = false,
    serialProcessing = true
) { event ->
    render(event)
}
```

### `serialProcessing` 说明

当 `onlyReceiveLatest = false` 时生效。

#### `serialProcessing = true`（默认）

- 使用 `Mutex`
- 按顺序处理
- 适合顺序敏感场景

例如：消息列表、订单状态流转、日志顺序写入、队列型任务

#### `serialProcessing = false`

- 每个事件独立协程处理
- 吞吐更高
- 不保证顺序
- 适合互不依赖的独立任务

例如：多个互不相关的 UI 刷新、埋点上报、非顺序关键型处理

> ⚠️ 注意：`serialProcessing = false` 不是"自动多线程"。  
> 因为默认 `FlowScope` 在 `Dispatchers.Main.immediate` 上运行。  
> 如果 `block` 很重，请手动切到 `Dispatchers.IO` 或 `Dispatchers.Default`。

---

## SharedFlow 缓冲区模型

核心配置来自：

```kotlin
MutableSharedFlow(
    replay = config.replaySize,
    extraBufferCapacity = config.extraBufferCapacity,
    onBufferOverflow = config.bufferOverflow
)
```

### 1. 三个参数分别代表什么

#### `replaySize`

给**新订阅者**重放的历史事件数量。

- `0`：不保留历史
- `1`：新订阅者进来立刻拿到最后一条
- `N`：新订阅者拿到最近 N 条

#### `extraBufferCapacity`

给当前系统增加的额外缓冲区，主要用于应对：

- 短时间内高频发送
- 收集端来不及消费
- 减少发送端阻塞

#### `bufferOverflow`

当缓冲区满时，如何处理新到来的事件。

### 2. 一个容易误解的点

> **`SUSPEND` 不等于"永久不丢消息"。**

在 `SharedFlow` 中：

- **有慢订阅者** 且 **缓冲区满** 时，`SUSPEND` 会挂起发送者
- 但如果 **当前根本没有订阅者**，且 `replaySize = 0`，那发送出去的事件并不会为"未来订阅者"保留

也就是说：

- 你想让"后来才订阅的人"也能收到历史值，需要设置 `replaySize > 0`
- 仅仅设置 `SUSPEND` 不够

### 3. 什么时候该设置 `replaySize > 0`

**适合：**

- 当前状态类事件
- 页面恢复时希望拿到最近一次状态
- 最后一条进度、最后一条位置、最后一条连接状态

**不适合：**

- 一次性消费事件，如 Toast、导航、支付弹窗  
  否则新页面重建时可能收到旧事件

---

## BufferOverflow 策略详解

### 策略对比表

| 策略 | 含义 | 优点 | 缺点 | 适合场景 | 不适合场景 |
|---|---|---|---|---|---|
| `SUSPEND` | 缓冲区满时挂起发送者 | 尽量不丢事件 | 发送方可能变慢甚至等待 | 支付、订单、IM、关键通知 | 高频 UI 状态流 |
| `DROP_OLDEST` | 丢掉最旧事件，保留新事件 | 始终尽量保留最新状态 | 历史事件会丢 | 进度、定位、传感器、行情 | 订单、支付、消息 |
| `DROP_LATEST` | 丢掉最新事件，保留旧事件 | 保护已在缓冲区中的数据 | 新事件会被忽略，语义较少见 | 极少数"保旧弃新"场景 | 大多数实时场景 |

### 1. `SUSPEND`

默认推荐，尤其适合关键业务：IM 消息、订单状态、支付结果、业务审批流、数据同步

```kotlin
FlowEventBus.init(
    FlowEventBusConfig.Builder()
        .setReplaySize(0)
        .setExtraBufferCapacity(2048)
        .setBufferOverflow(BufferOverflow.SUSPEND)
        .build()
)
```

建议搭配：`receiveEvent` 或 `receiveEventLive(onlyReceiveLatest = false, serialProcessing = true)`

### 2. `DROP_OLDEST`

只关心最新状态时非常合适：上传进度、下载进度、用户当前位置、传感器流、股票行情、播放器当前位置

```kotlin
FlowEventBus.init(
    FlowEventBusConfig.Builder()
        .setReplaySize(1)
        .setExtraBufferCapacity(128)
        .setBufferOverflow(BufferOverflow.DROP_OLDEST)
        .build()
)
```

建议搭配：`receiveEventLive(onlyReceiveLatest = true)`

效果：

1. 总线层面保留最新数据
2. UI 层面回到前台只处理最后一次值
3. 避免后台堆积大量无意义旧事件

### 3. `DROP_LATEST`

极少使用，适合"缓冲区里的旧数据更重要"的场景：

```kotlin
FlowEventBus.init(
    FlowEventBusConfig.Builder()
        .setReplaySize(0)
        .setExtraBufferCapacity(128)
        .setBufferOverflow(BufferOverflow.DROP_LATEST)
        .build()
)
```

> 一般业务里不建议优先选它，除非你非常清楚"为什么要保旧弃新"。

---

## 典型使用示例

### 1. 支付成功事件：不能丢，且顺序敏感

```kotlin
// 初始化（全局默认 Bus）
FlowEventBus.init(
    FlowEventBusConfig.Builder()
        .setReplaySize(0)
        .setExtraBufferCapacity(2048)
        .setBufferOverflow(BufferOverflow.SUSPEND)
        .build()
)

// 发送
sendEvent(OrderPaidEvent("A1001", 19900), tag = "pay")

// 接收
receiveEvent<OrderPaidEvent>("pay") { event ->
    showToast("支付成功：${event.orderId}")
}
```

**为什么这样选：** 关键业务不能随便丢，`SUSPEND` 比较稳妥，`replay=0` 避免页面重建后重复消费旧支付事件。

### 2. 上传进度：只关心最新值

```kotlin
receiveEventLive<UploadProgressEvent>(
    "upload",
    onlyReceiveLatest = true
) { event ->
    progressBar.progress = event.progress
}
```

**为什么这样选：** 30%、31%、32%……这些中间值不重要，只要最后的值即可。`DROP_OLDEST + onlyReceiveLatest = true` 是高频状态流的经典组合。

### 3. 聊天消息：后台缓存，前台按顺序处理

```kotlin
receiveEventLive<ChatMessageEvent>(
    "chat_room_1",
    onlyReceiveLatest = false,
    serialProcessing = true
) { event ->
    appendMessage(event)
}
```

**为什么这样选：** 聊天消息不能只要最后一条，顺序非常重要，页面不活跃时先缓存，回到前台后按顺序补处理。

### 4. 纯标签事件：页面刷新

```kotlin
// 发送
sendTag("refresh_home")

// 接收
receiveTag("refresh_home") {
    refreshHomeData()
}
```

**适用场景：** 页面刷新、退出登录广播、打开某个弹窗、通知某组件重新加载。

> ⚠️ 注意：`receiveTag / receiveTagLive` 会过滤掉 `null` 或空白 tag，不要传空字符串。

### 5. 非生命周期场景接收：记得手动取消

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

适合：Service、全局模块、长生命周期管理器、非 `LifecycleOwner` 对象。

### 6. 独立 Bus：聊天模块与行情模块隔离

```kotlin
// 定义独立 Bus
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

// 定义事件
data class ChatEvent(val from: String, val message: String)
data class PriceEvent(val symbol: String, val price: Double)
```

**发送：**

```kotlin
// 发送到 chatBus（pricingBus 和全局 Bus 收不到）
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)

// 发送到 pricingBus（chatBus 和全局 Bus 收不到）
sendEventSuspend(PriceEvent("AAPL", 185.5), bus = AppBus.pricingBus)

// 发送标签到 chatBus
sendTag("chat_typing", bus = AppBus.chatBus)
```

**接收：**

```kotlin
// 接收 chatBus 聊天消息（保证不丢，按顺序处理）
receiveEventLive<ChatEvent>(
    onlyReceiveLatest = false,
    serialProcessing = true,
    bus = AppBus.chatBus
) { event ->
    appendMessage("${event.from}: ${event.message}")
}

// 接收 pricingBus 行情（只要最新价格）
receiveEventLive<PriceEvent>(
    onlyReceiveLatest = true,
    bus = AppBus.pricingBus
) { event ->
    updatePriceDisplay("${event.symbol}: $${event.price}")
}

// 接收 chatBus 标签
receiveTag("chat_typing", bus = AppBus.chatBus) { tag ->
    showTypingIndicator()
}
```

**为什么这样选：**

- 聊天用 `SUSPEND` 保证消息不丢失，`serialProcessing = true` 保证消息顺序
- 行情用 `DROP_OLDEST` + `replaySize = 1`，只关心最新价格，新订阅者立刻拿到最近一条
- 两个模块各自独立，互不干扰：聊天消息不会出现在行情 Bus 上，反之亦然

### 7. 独立 Bus：不绑定生命周期的接收

```kotlin
private var chatHandlerJob: Job? = null

fun startChatListen() {
    chatHandlerJob = receiveEventHandler<ChatEvent>(bus = AppBus.chatBus) { event ->
        logChatMessage(event)
    }
}

fun stopChatListen() {
    chatHandlerJob?.cancel()  // 必须手动取消！
}
```

---

## 最佳实践

### 1. 在 `Application` 中尽早初始化全局 Bus

否则可能已经被默认配置自动初始化，自定义配置失效。

### 2. 关键业务优先选 `SUSPEND`

如：支付、订单、消息、状态机流转。

### 3. 高频状态流优先选 `DROP_OLDEST`

搭配 `replaySize = 1` 和 `onlyReceiveLatest = true`。

### 4. 一次性事件不要轻易开 `replay > 0`

否则新订阅者可能拿到旧事件。

### 5. UI 回调默认运行在主线程，重活请切线程

```kotlin
receiveEvent<OrderPaidEvent> { event ->
    withContext(Dispatchers.IO) {
        saveToDb(event)
    }
}
```

### 6. `receiveEventHandler / receiveTagHandler` 必须手动取消

否则可能造成内存泄漏。使用独立 Bus 时也一样：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    handlerJob?.cancel()
    chatHandlerJob?.cancel()
}
```

### 7. `lifeEvent = ON_STOP` 要谨慎

因为这不是"暂停接收"，而是**直接取消订阅**。  
如果你想"后台不处理、前台再处理"，请优先使用 `receiveEventLive`。

### 8. 注意 `receiveEventLive` 的缓存增长

当 `onlyReceiveLatest = false` 时，后台期间会用队列缓存全部事件。  
如果事件量特别大，内存会增长。此时考虑：

- 改成 `onlyReceiveLatest = true`
- 缩小事件粒度
- 做业务限流
- 使用 `DROP_OLDEST`

### 9. 独立 Bus 集中管理

建议使用 `object` 单例（如 `AppBus`）统一管理所有独立 Bus 实例，方便查找和维护：

```kotlin
object AppBus {
    val chatBus = FlowEventBus.create(chatConfig)
    val pricingBus = FlowEventBus.create(pricingConfig)
    val logBus = FlowEventBus.create(logConfig)
}
```

### 10. 不要在全局 Bus 和独立 Bus 之间混淆

发送和接收必须使用**同一个 Bus 实例**，否则收不到事件：

```kotlin
// ❌ 错误：发到 chatBus，但在全局 Bus 上接收
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)
receiveEvent<ChatEvent> { event -> /* 收不到！ */ }

// ✅ 正确：发送和接收使用同一个 Bus
sendEvent(ChatEvent("张三", "你好"), bus = AppBus.chatBus)
receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event -> /* ✅ 收到 */ }
```

---

## 常见问题 FAQ

### Q1：为什么我的自定义配置不生效？

因为全局 Bus 可能已经被默认初始化了。请确保：

- 在 `Application.onCreate()` 中最早调用 `FlowEventBus.init(...)`
- 先初始化，再调用任何 send / receive

> 💡 独立 Bus 不存在此问题，因为它在 `create()` 时就直接传入配置。

### Q2：为什么我发的事件后来订阅的人收不到？

因为默认 `replaySize = 0`，`SharedFlow` 不是消息队列。如果你希望"后来订阅的人"拿到最近值：

```kotlin
setReplaySize(1)
```

### Q3：为什么 `SUSPEND` 也会出现"后来的人收不到历史消息"？

因为 `SUSPEND` 解决的是"缓冲区满时怎么处理"，不是"给未来订阅者保存历史值"。保存历史值靠的是 `replaySize`。

### Q4：为什么 `receiveTag()` 没反应？

请检查：

- tag 是否是 `null`
- tag 是否是空字符串或空白字符串
- 传入的监听 tag 是否匹配
- **是否使用了同一个 Bus 实例**

### Q5：为什么我设置了 `serialProcessing = false`，还是会卡 UI？

因为默认作用域是 `Dispatchers.Main.immediate`。"并发"只是多个协程同时启动，不代表 CPU 重活自动切到后台线程。

正确做法：

```kotlin
receiveEventLive<HeavyEvent>(
    onlyReceiveLatest = false,
    serialProcessing = false
) { event ->
    withContext(Dispatchers.Default) {
        doHeavyWork(event)
    }
}
```

### Q6：什么时候该用 `receiveEvent`，什么时候该用 `receiveEventLive`？

- **业务可立即处理**：`receiveEvent`
- **必须等页面活跃再处理**：`receiveEventLive`

简单理解：`receiveEvent` = 直接消费，`receiveEventLive` = 先收着，前台再处理。

### Q7：什么时候该用独立 Bus？

| 情况 | 建议 |
|---|---|
| 简单的全局事件通信 | 全局 Bus 即可，不传 `bus` 参数 |
| 不同模块需要不同的 BufferOverflow 策略 | 用独立 Bus |
| 防止模块间事件串扰 | 用独立 Bus |
| SDK / 插件内部通信，不想影响宿主 App | 用独立 Bus |
| 单元测试 / 集成测试 | 用独立 Bus（避免全局状态污染） |

### Q8：独立 Bus 能和全局 Bus 一起用吗？

可以。它们完全独立，互不影响。你可以同时使用全局 Bus 进行通用事件通信，使用独立 Bus 进行特定模块的隔离通信：

```kotlin
// 全局 Bus 的登录事件
receiveEvent<LoginEvent> { event -> updateUI(event) }

// chatBus 的聊天消息
receiveEvent<ChatEvent>(bus = AppBus.chatBus) { event -> appendChat(event) }

// pricingBus 的行情数据
receiveEventLive<PriceEvent>(
    onlyReceiveLatest = true,
    bus = AppBus.pricingBus
) { event -> updatePrice(event) }
```

### Q9：独立 Bus 需要在 Application 中初始化吗？

不需要。独立 Bus 通过 `FlowEventBus.create(config)` 直接创建，配置在创建时就已确定。只有全局默认 Bus 需要通过 `FlowEventBus.init(config)` 初始化。

---

## 推荐配置速查表

### 全局 Bus 推荐配置

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
| 传感器模块 | 1 | 64 | `DROP_OLDEST` | 只要最新采样 |
| 测试专用 | 0 | 256 | `SUSPEND` | 简单够用即可 |

---

## 总结

FlowEventBus 的核心思路：

1. 用 `SharedFlow` 做事件总线
2. 用 `LifecycleOwner + FlowScope` 管理页面订阅生命周期
3. 用 `LiveData` 把某些事件延后到页面活跃时再处理
4. 用 `BufferOverflow`、`replaySize`、`extraBufferCapacity` 控制事件缓存和背压策略
5. 用**全局 Bus + 独立 Bus** 实现灵活的事件隔离

如果你把它简单归类：

- **关键业务不丢**：`SUSPEND`
- **高频状态保最新**：`DROP_OLDEST + onlyReceiveLatest = true`
- **极少数保旧弃新**：`DROP_LATEST`
- **模块隔离**：`FlowEventBus.create()` 创建独立 Bus
- **通用通信**：直接使用全局 Bus（不传 `bus` 参数）

