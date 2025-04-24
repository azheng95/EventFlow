代码参考Channel项目实现：https://github.com/liangjingkanji/Channel


[![](https://jitpack.io/v/azheng95/EventFlow.svg)](https://jitpack.io/#azheng95/EventFlow)

### 核心设计
- 基于SharedFlow的事件总线：
使用MutableSharedFlow作为事件发布通道,
配置了较大的缓冲区(102400)以避免背压问题,
溢出策略为丢弃最旧的事件(DROP_OLDEST)

- 生命周期感知：
大多数函数都与Android生命周期组件集成,
默认在组件销毁时自动取消事件订阅，防止内存泄漏

- 灵活的事件发送机制：
支持带标签的事件发送,
提供生命周期感知和非生命周期感知的API,
包含挂起函数版本用于协程内使用

- 多样化的事件接收方式：
支持按类型和标签过滤事件,
提供LiveData集成，确保事件在UI可见时处理,
可选择是否只接收最新事件

### 添加依赖

在 `settings.gradle` 文件中加入

```groovy
dependencyResolutionManagement {
    repositories {
        // JitPack 远程仓库：https://jitpack.io
        maven { url 'https://jitpack.io' }
    }
}
```
或者在 `settings.gradle.kts` 文件中加入

```groovy
dependencyResolutionManagement {
    repositories {
        // JitPack 远程仓库：https://jitpack.io
        maven { url = uri("https://jitpack.io") }
    }
}
```


在项目 app 模块下的 `build.gradle` 文件中加入远程依赖

```groovy
dependencies {
	        implementation 'com.github.azheng95:EventFlow:0.0.3'
}
```

### 使用方式
发送
```groovy
sendEvent("任何对象")
```
接收
```groovy
receiveEvent<String>() {
    tv.text = it
}
```
sendEvent的参数和receiveEvent的泛型类型匹配即可接受事件, 可以是任何对象
 
标签
如果类型重复, 可以通过加标签来区分事件

创建一个事件类
```groovy
data class UserInfoEvent(val name:String, val age:Int)
```
发送
```groovy
sendEvent(UserInfoEvent("新的姓名", 24), "tag_change_name")
```
接收
```groovy
receiveEvent<UserInfoEvent>("tag_change_name", "tag_change_username") {
    tv.text = it.name // it 即为UserInfoEvent
}
```

标签可以是多个, 只要匹配一个标签, 就可成功接收事件
建议遵守前缀tag_命名规范, 方便全局搜索标签来定位事件

生命周期
指定取消生命周期 默认在Lifecycle.Event.ON_DESTROY销毁, 但是可以指定其参数
```groovy
receiveEvent<String>(lifeEvent = Lifecycle.Event.ON_PAUSE) {
    tv.text = it
}
```

手动注销
使用receiveEventHandler可返回用于手动取消事件的对象
```groovy
val receiver = receiveEventHandler<String> {
    tv.text = it
}

receiver.cancel() // 手动调用函数注销
```

将消息延迟到前台接收,onlyReceiveLatest 回到前台后是否只接收最后一次的值，默认为false

```groovy
receiveEventLive<String>(onlyReceiveLatest = true) {
    tv.text = it
}
```

可选：
 在 Application 使用自定义配置
 
```groovy
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 使用自定义配置
        val config = FlowEventBusConfig.Builder()
            .setReplaySize(0)
            .setExtraBufferCapacity(1024) // 设置较小的缓冲区
            .setBufferOverflow(BufferOverflow.DROP_OLDEST) // 更改溢出策略
            .build()

        FlowEventBus.init(config)

    }
}
```



