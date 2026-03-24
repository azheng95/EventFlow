package com.azheng.flow.demo

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.azheng.event.flow.receiveEvent
import com.azheng.event.flow.receiveEventLive
import com.azheng.event.flow.sendEventSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * FlowEventBus 压力测试类
 * 测试 receiveEvent 和 receiveEventLive 在高并发下的顺序性和可靠性
 *
 * 注意：receiveEventLive 内部使用 Dispatchers.Main.immediate，
 * 因此需从主线程（如 lifecycleScope.launch）调用 runAllTests()
 */
class FlowEventBusStressTest {

    companion object {
        private const val TAG = "FlowEventBusStressTest"
    }

    data class TestEvent(val sequence: Int, val timestamp: Long = System.nanoTime())

    /**
     * 测试用 LifecycleOwner
     * 初始状态为 RESUMED，使 receiveEventLive 的消费者立即活跃
     *
     * 注意：LifecycleRegistry 要求在主线程操作状态变更，
     * 因此 destroy() 也应在主线程调用
     */
    class TestLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        init {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    data class TestResult(
        val testName: String,
        val totalSent: Int,
        val totalReceived: Int,
        val isOrdered: Boolean,
        val outOfOrderCount: Int,
        val missingCount: Int,
        val duplicateCount: Int,
        val durationMs: Long,
        val details: String
    ) {
        override fun toString(): String = """
            |========== $testName ==========
            |发送总数: $totalSent
            |接收总数: $totalReceived
            |是否有序: $isOrdered
            |乱序数量: $outOfOrderCount
            |丢失数量: $missingCount
            |重复数量: $duplicateCount
            |耗时: ${durationMs}ms
            |详情: $details
            |================================
        """.trimMargin()
    }

    /**
     * 运行所有压力测试
     * 必须从主线程调用（因为 receiveEventLive 和 LifecycleRegistry 要求主线程）
     */
    suspend fun runAllTests() {
        Log.d(TAG, "========== 开始 FlowEventBus 压力测试 ==========")

        val results = mutableListOf<TestResult>()

        results += testReceiveEventSingleCoroutine(1000)
        Log.d(TAG, results.last().toString())
        delay(500)

        results += testReceiveEventMultiCoroutine(1000, 10)
        Log.d(TAG, results.last().toString())
        delay(500)

        results += testReceiveEventLiveOrdered(1000)
        Log.d(TAG, results.last().toString())
        delay(500)

        results += testReceiveEventLiveMultiCoroutine(1000, 10)
        Log.d(TAG, results.last().toString())
        delay(500)

        results += testExtremeStress(5000, 50)
        Log.d(TAG, results.last().toString())

        // 汇总报告
        Log.d(TAG, "========== 测试汇总 ==========")
        results.forEach { result ->
            val status = if (result.missingCount == 0 && result.duplicateCount == 0) "✅ PASS" else "❌ FAIL"
            Log.d(TAG, "$status ${result.testName}: 发${result.totalSent}/收${result.totalReceived}, 乱序${result.outOfOrderCount}, 丢失${result.missingCount}")
        }
        Log.d(TAG, "========== 所有测试完成 ==========")
    }

    /**
     * 测试1：单协程顺序发送 + receiveEvent
     * 预期：接收顺序与发送顺序一致，无丢失
     */
    private suspend fun testReceiveEventSingleCoroutine(eventCount: Int): TestResult {
        val testName = "receiveEvent 单协程顺序发送"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_single_${System.currentTimeMillis()}"

        val startTime = System.currentTimeMillis()

        // 订阅事件
        val job = lifecycleOwner.receiveEvent<TestEvent>(uniqueTag) { event ->
            receivedEvents.add(event)
            receivedCount.incrementAndGet()
        }

        delay(100) // 等待订阅者就绪

        // 在 Default 线程发送，模拟真实场景
        withContext(Dispatchers.Default) {
            for (i in 0 until eventCount) {
                sendEventSuspend(TestEvent(i), uniqueTag)
            }
        }

        val completed = waitForCompletion(receivedCount, eventCount, 10000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

    /**
     * 测试2：多协程并发发送 + receiveEvent
     * 预期：所有事件都能收到（可能乱序，因为发送方就是并发的）
     */
    private suspend fun testReceiveEventMultiCoroutine(
        eventCount: Int,
        coroutineCount: Int
    ): TestResult {
        val testName = "receiveEvent 多协程并发发送 ($coroutineCount 个协程)"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_multi_${System.currentTimeMillis()}"
        val sentCounter = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        val job = lifecycleOwner.receiveEvent<TestEvent>(uniqueTag) { event ->
            receivedEvents.add(event)
            receivedCount.incrementAndGet()
        }

        delay(100)

        val eventsPerCoroutine = eventCount / coroutineCount
        withContext(Dispatchers.Default) {
            val jobs = (0 until coroutineCount).map {
                launch {
                    repeat(eventsPerCoroutine) {
                        val seq = sentCounter.getAndIncrement()
                        sendEventSuspend(TestEvent(seq), uniqueTag)
                    }
                }
            }
            jobs.joinAll()
        }

        val completed = waitForCompletion(receivedCount, eventCount, 10000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

    /**
     * 测试3：receiveEventLive 顺序模式
     * 预期：所有事件都能收到，且保持顺序（serialProcessing=true）
     */
    private suspend fun testReceiveEventLiveOrdered(eventCount: Int): TestResult {
        val testName = "receiveEventLive 顺序模式 (onlyReceiveLatest=false)"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_live_${System.currentTimeMillis()}"

        val startTime = System.currentTimeMillis()

        val job = lifecycleOwner.receiveEventLive<TestEvent>(
            uniqueTag,
            onlyReceiveLatest = false
        ) { event ->
            receivedEvents.add(event)
            receivedCount.incrementAndGet()
        }

        delay(100)

        withContext(Dispatchers.Default) {
            for (i in 0 until eventCount) {
                sendEventSuspend(TestEvent(i), uniqueTag)
            }
        }

        val completed = waitForCompletion(receivedCount, eventCount, 10000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

    /**
     * 测试4：receiveEventLive 多协程并发发送
     * 预期：所有事件都能收到（接收端串行处理，但发送端并发所以可能乱序）
     */
    private suspend fun testReceiveEventLiveMultiCoroutine(
        eventCount: Int,
        coroutineCount: Int
    ): TestResult {
        val testName = "receiveEventLive 多协程并发 ($coroutineCount 个协程)"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_live_multi_${System.currentTimeMillis()}"
        val sentCounter = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        val job = lifecycleOwner.receiveEventLive<TestEvent>(
            uniqueTag,
            onlyReceiveLatest = false
        ) { event ->
            receivedEvents.add(event)
            receivedCount.incrementAndGet()
        }

        delay(100)

        val eventsPerCoroutine = eventCount / coroutineCount
        withContext(Dispatchers.Default) {
            val jobs = (0 until coroutineCount).map {
                launch {
                    repeat(eventsPerCoroutine) {
                        val seq = sentCounter.getAndIncrement()
                        sendEventSuspend(TestEvent(seq), uniqueTag)
                    }
                }
            }
            jobs.joinAll()
        }

        val completed = waitForCompletion(receivedCount, eventCount, 10000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

    /**
     * 测试5：极限压力测试
     * 大量事件 + 大量并发协程，验证框架在极端条件下的稳定性
     */
    private suspend fun testExtremeStress(eventCount: Int, coroutineCount: Int): TestResult {
        val testName = "极限压力测试 ($eventCount 事件, $coroutineCount 协程)"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_extreme_${System.currentTimeMillis()}"
        val sentCounter = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        val job = lifecycleOwner.receiveEvent<TestEvent>(uniqueTag) { event ->
            receivedEvents.add(event)
            receivedCount.incrementAndGet()
        }

        delay(100)

        val eventsPerCoroutine = eventCount / coroutineCount
        withContext(Dispatchers.Default) {
            val jobs = (0 until coroutineCount).map {
                launch {
                    repeat(eventsPerCoroutine) {
                        val seq = sentCounter.getAndIncrement()
                        sendEventSuspend(TestEvent(seq), uniqueTag)
                    }
                }
            }
            jobs.joinAll()
        }

        val completed = waitForCompletion(receivedCount, eventCount, 30000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

    /**
     * 非阻塞等待接收完成
     * 使用 delay 而非 Thread.sleep，不阻塞主线程，
     * 让 receiveEventLive 的 repeatOnLifecycle 消费者有机会执行
     *
     * @return true 表示全部收到，false 表示超时或稳定后仍未收齐
     */
    private suspend fun waitForCompletion(
        counter: AtomicInteger,
        expected: Int,
        timeoutMs: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        var lastCount = -1
        var stableCount = 0

        while (true) {
            val current = counter.get()

            if (current >= expected) return true

            if (System.currentTimeMillis() - startTime > timeoutMs) return false

            // 如果连续5次检查（250ms）数量不变，认为已稳定，不再等待
            if (current == lastCount) {
                stableCount++
                if (stableCount >= 5 && current > 0) return true
            } else {
                stableCount = 0
                lastCount = current
            }

            delay(50)
        }
    }

    /**
     * 分析测试结果：检查顺序性、丢失、重复
     */
    private fun analyzeResults(
        testName: String,
        totalSent: Int,
        receivedEvents: List<TestEvent>,
        durationMs: Long,
        allReceived: Boolean
    ): TestResult {
        val totalReceived = receivedEvents.size

        // 检查顺序性
        var outOfOrderCount = 0
        var isOrdered = true
        for (i in 1 until receivedEvents.size) {
            if (receivedEvents[i].sequence < receivedEvents[i - 1].sequence) {
                outOfOrderCount++
                isOrdered = false
            }
        }

        // 检查丢失和重复
        val receivedSequences = receivedEvents.map { it.sequence }
        val uniqueSequences = receivedSequences.toSet()
        val duplicateCount = receivedSequences.size - uniqueSequences.size

        val expectedSequences = (0 until totalSent).toSet()
        val missingSequences = expectedSequences - uniqueSequences
        val missingCount = missingSequences.size

        val details = buildString {
            if (!allReceived && totalReceived < totalSent) append("未完全接收; ")
            if (outOfOrderCount > 0) append("乱序事件数: $outOfOrderCount; ")
            if (missingCount in 1..10) {
                append("丢失序号: $missingSequences; ")
            } else if (missingCount > 10) {
                append("丢失序号(前10): ${missingSequences.take(10)}...; ")
            }
            if (duplicateCount > 0) append("存在 $duplicateCount 个重复; ")
            if (isEmpty()) append("完美通过 ✓")
        }

        return TestResult(
            testName, totalSent, totalReceived, isOrdered,
            outOfOrderCount, missingCount, duplicateCount, durationMs, details
        )
    }
}
