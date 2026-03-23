package com.azheng.flow.demo

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.azheng.event.flow.FlowEventBus
import com.azheng.event.flow.FlowEventBusConfig
import com.azheng.event.flow.receiveEvent
import com.azheng.event.flow.receiveEventLive
import com.azheng.event.flow.sendEventSuspend
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * FlowEventBus 压力测试类
 * 测试 receiveEvent 和 receiveEventLive 在高并发下的顺序性
 *//**
 * FlowEventBus 压力测试类 - 修复版
 */
class FlowEventBusStressTest {

    companion object {
        private const val TAG = "FlowEventBusStressTest"
    }

    data class TestEvent(val sequence: Int, val timestamp: Long = System.nanoTime())

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

    suspend fun runAllTests() {
        Log.d(TAG, "========== 开始 FlowEventBus 压力测试 ==========")



        val result1 = testReceiveEventSingleCoroutine(1000)
        Log.d(TAG, result1.toString())
        delay(500)

        val result2 = testReceiveEventMultiCoroutine(1000, 10)
        Log.d(TAG, result2.toString())
        delay(500)

        val result3 = testReceiveEventLiveOrdered(1000)
        Log.d(TAG, result3.toString())
        delay(500)

        val result4 = testReceiveEventLiveMultiCoroutine(1000, 10)
        Log.d(TAG, result4.toString())
        delay(500)

        val result5 = testExtremeStress(5000, 50)
        Log.d(TAG, result5.toString())

        Log.d(TAG, "========== 所有测试完成 ==========")
    }

    private suspend fun testReceiveEventSingleCoroutine(eventCount: Int): TestResult {
        val testName = "receiveEvent 单协程顺序发送"
        val receivedEvents = CopyOnWriteArrayList<TestEvent>()
        val lifecycleOwner = TestLifecycleOwner()
        val receivedCount = AtomicInteger(0)
        val uniqueTag = "test_single_${System.currentTimeMillis()}"

        val startTime = System.currentTimeMillis()

        val job = lifecycleOwner.receiveEvent<TestEvent>(uniqueTag) { event ->
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

        // ✅ 关键修复：使用非阻塞等待
        val completed = waitForCompletion(receivedCount, eventCount, 10000)
        val duration = System.currentTimeMillis() - startTime

        job.cancel()
        lifecycleOwner.destroy()

        return analyzeResults(testName, eventCount, receivedEvents, duration, completed)
    }

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
     * ✅ 非阻塞等待 - 关键修复
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

            // 如果连续5次检查数量不变，认为已完成
            if (current == lastCount) {
                stableCount++
                if (stableCount >= 5 && current > 0) return true
            } else {
                stableCount = 0
                lastCount = current
            }

            delay(50) // ✅ 非阻塞，让主线程有机会处理 LiveData 回调
        }
    }

    private fun analyzeResults(
        testName: String,
        totalSent: Int,
        receivedEvents: List<TestEvent>,
        durationMs: Long,
        allReceived: Boolean
    ): TestResult {
        val totalReceived = receivedEvents.size

        var outOfOrderCount = 0
        var isOrdered = true
        for (i in 1 until receivedEvents.size) {
            if (receivedEvents[i].sequence < receivedEvents[i - 1].sequence) {
                outOfOrderCount++
                isOrdered = false
            }
        }

        val receivedSequences = receivedEvents.map { it.sequence }
        val uniqueSequences = receivedSequences.toSet()
        val duplicateCount = receivedSequences.size - uniqueSequences.size

        val expectedSequences = (0 until totalSent).toSet()
        val missingSequences = expectedSequences - uniqueSequences
        val missingCount = missingSequences.size

        val details = buildString {
            if (!allReceived && totalReceived < totalSent) append("未完全接收; ")
            if (outOfOrderCount > 0) append("乱序事件数: $outOfOrderCount; ")
            if (missingCount > 0 && missingCount <= 10) {
                append("丢失序号: $missingSequences; ")
            } else if (missingCount > 10) {
                append("丢失序号(前10): ${missingSequences.take(10)}...; ")
            }
            if (duplicateCount > 0) append("存在 $duplicateCount 个重复; ")
            if (isEmpty()) append("完美通过 ✓")
        }

        return TestResult(testName, totalSent, totalReceived, isOrdered,
            outOfOrderCount, missingCount, duplicateCount, durationMs, details)
    }
}
