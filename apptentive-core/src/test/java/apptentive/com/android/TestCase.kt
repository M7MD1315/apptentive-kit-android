package apptentive.com.android

import apptentive.com.android.concurrent.ImmediateExecutorQueue
import apptentive.com.android.core.DependencyProvider
import apptentive.com.android.core.ExecutorFactory
import apptentive.com.android.core.Logger
import apptentive.com.android.core.Provider
import apptentive.com.android.util.LogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before

open class TestCase(
    private val logMessages: Boolean = false,
    private val logStackTraces: Boolean = false
) {
    private val results = mutableListOf<Any>()

    //region Before/After

    @Before
    open fun setUp() {
        DependencyProvider.register(createPlatformLoggerProvider())
        DependencyProvider.register(createExecutionQueueFactoryProvider())
        results.clear()
    }

    @After
    open fun tearDown() {
        DependencyProvider.clear()
    }

    //endregion

    //region Factory

    private fun createExecutionQueueFactoryProvider(): Provider<ExecutorFactory> =
        object : Provider<ExecutorFactory> {
            override fun get(): ExecutorFactory {
                return MockExecutorFactory
            }

        }

    private fun createPlatformLoggerProvider(): Provider<Logger> =
        object : Provider<Logger> {
            override fun get(): Logger {
                return MockLogger(logMessages, logStackTraces)
            }
        }

    //endregion

    //region Results

    protected fun addResult(result: Any) {
        results.add(result)
    }

    protected fun assertResults(vararg expected: Any, clearResults: Boolean = true) {
        assertEquals(expected.toList(), results)
        if (clearResults) {
            results.clear()
        }
    }

    //endregion
}

object MockExecutorFactory : ExecutorFactory {
    override fun createMainQueue() = ImmediateExecutorQueue("main")
    override fun createSerialQueue(name: String) = ImmediateExecutorQueue(name)
    override fun createConcurrentQueue(name: String, maxConcurrentTasks: Int?) =
        ImmediateExecutorQueue(name)
}

private class MockLogger(
    private val logMessages: Boolean,
    private val logStackTraces: Boolean
) : Logger {
    override fun log(logLevel: LogLevel, message: String) {
        if (logMessages) {
            print(message)
        }
    }

    override fun log(logLevel: LogLevel, throwable: Throwable) {
        if (logStackTraces) {
            throwable.printStackTrace()
        }
    }

    override fun isMainQueue(): Boolean = false
}