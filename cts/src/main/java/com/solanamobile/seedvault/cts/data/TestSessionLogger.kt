/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

import android.content.Context
import android.util.Log
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

interface TestSessionLogger : Logger {
    fun resetSession()

    fun getSessionFilename(): String
}

@Singleton
internal class TestSessionLoggerImpl @Inject constructor(
    @ApplicationContext private val ctx: Context
) : TestSessionLogger {
    companion object {
        private val TAG = TestSessionLogger::class.simpleName!!
    }

    private val logger: AtomicReference<Logger> by lazy {
        AtomicReference(doConfigureLogger())
    }

    private fun doConfigureLogger(): Logger {
        val name = "test_session_${LocalDateTime.now(Clock.systemUTC())}.txt"

        // reset the default context (which may already have been initialized)
        // since we want to reconfigure it
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.stop()

        // setup FileAppender
        val encoder1 = PatternLayoutEncoder()
        encoder1.context = lc
        encoder1.pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level - %msg%n"
        encoder1.start()

        val fileAppender = FileAppender<ILoggingEvent>()
        fileAppender.context = lc
        fileAppender.file = ctx.getFileStreamPath(name).absolutePath
        fileAppender.encoder = encoder1
        fileAppender.start()

        // setup LogcatAppender
        val encoder2 = PatternLayoutEncoder()
        encoder2.context = lc
        encoder2.pattern = "[%thread] %msg%n"
        encoder2.start()

        val logcatAppender = LogcatAppender()
        logcatAppender.context = lc
        logcatAppender.encoder = encoder2
        logcatAppender.start()

        Log.d(TAG, "Creating test session logger '$name'")

        return lc.getLogger(name).also {
            it.addAppender(fileAppender)
            it.addAppender(logcatAppender)
        }
    }

    override fun resetSession() {
        logger.set(doConfigureLogger())
    }

    override fun getSessionFilename(): String = logger.get().name

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // PASS-THROUGH TO WRAPPED LOGGER
    // /////////////////////////////////////////////////////////////////////////////////////////////

    override fun getName(): String = logger.get().name
    override fun isTraceEnabled(): Boolean = logger.get().isTraceEnabled
    override fun isTraceEnabled(marker: Marker?): Boolean = logger.get().isTraceEnabled(marker)
    override fun trace(msg: String?) = logger.get().trace(msg)
    override fun trace(format: String?, arg: Any?) = logger.get().trace(format, arg)
    override fun trace(format: String?, arg1: Any?, arg2: Any?) = logger.get().trace(format, arg1, arg2)
    override fun trace(format: String?, vararg arguments: Any?) = logger.get().trace(format, arguments)
    override fun trace(msg: String?, t: Throwable?) = logger.get().trace(msg, t)
    override fun trace(marker: Marker?, msg: String?) = logger.get().trace(marker, msg)
    override fun trace(marker: Marker?, format: String?, arg: Any?) = logger.get().trace(marker, format, arg)
    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = logger.get().trace(marker, format, arg1, arg2)
    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) = logger.get().trace(marker, format, argArray)
    override fun trace(marker: Marker?, msg: String?, t: Throwable?) = logger.get().trace(marker, msg, t)
    override fun isDebugEnabled(): Boolean = logger.get().isDebugEnabled
    override fun isDebugEnabled(marker: Marker?): Boolean = logger.get().isDebugEnabled(marker)
    override fun debug(msg: String?) = logger.get().debug(msg)
    override fun debug(format: String?, arg: Any?) = logger.get().debug(format, arg)
    override fun debug(format: String?, arg1: Any?, arg2: Any?) = logger.get().debug(format, arg1, arg2)
    override fun debug(format: String?, vararg arguments: Any?) = logger.get().debug(format, arguments)
    override fun debug(msg: String?, t: Throwable?) = logger.get().debug(msg, t)
    override fun debug(marker: Marker?, msg: String?) = logger.get().debug(marker, msg)
    override fun debug(marker: Marker?, format: String?, arg: Any?) = logger.get().debug(marker, format, arg)
    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = logger.get().debug(marker, format, arg1, arg2)
    override fun debug(marker: Marker?, format: String?, vararg argArray: Any?) = logger.get().debug(marker, format, argArray)
    override fun debug(marker: Marker?, msg: String?, t: Throwable?) = logger.get().debug(marker, msg, t)
    override fun isInfoEnabled(): Boolean = logger.get().isInfoEnabled
    override fun isInfoEnabled(marker: Marker?): Boolean = logger.get().isInfoEnabled(marker)
    override fun info(msg: String?) = logger.get().info(msg)
    override fun info(format: String?, arg: Any?) = logger.get().info(format, arg)
    override fun info(format: String?, arg1: Any?, arg2: Any?) = logger.get().info(format, arg1, arg2)
    override fun info(format: String?, vararg arguments: Any?) = logger.get().info(format, arguments)
    override fun info(msg: String?, t: Throwable?) = logger.get().info(msg, t)
    override fun info(marker: Marker?, msg: String?) = logger.get().info(marker, msg)
    override fun info(marker: Marker?, format: String?, arg: Any?) = logger.get().info(marker, format, arg)
    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = logger.get().info(marker, format, arg1, arg2)
    override fun info(marker: Marker?, format: String?, vararg argArray: Any?) = logger.get().info(marker, format, argArray)
    override fun info(marker: Marker?, msg: String?, t: Throwable?) = logger.get().info(marker, msg, t)
    override fun isWarnEnabled(): Boolean = logger.get().isWarnEnabled
    override fun isWarnEnabled(marker: Marker?): Boolean = logger.get().isWarnEnabled(marker)
    override fun warn(msg: String?) = logger.get().warn(msg)
    override fun warn(format: String?, arg: Any?) = logger.get().warn(format, arg)
    override fun warn(format: String?, arg1: Any?, arg2: Any?) = logger.get().warn(format, arg1, arg2)
    override fun warn(format: String?, vararg arguments: Any?) = logger.get().warn(format, arguments)
    override fun warn(msg: String?, t: Throwable?) = logger.get().warn(msg, t)
    override fun warn(marker: Marker?, msg: String?) = logger.get().warn(marker, msg)
    override fun warn(marker: Marker?, format: String?, arg: Any?) = logger.get().warn(marker, format, arg)
    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = logger.get().warn(marker, format, arg1, arg2)
    override fun warn(marker: Marker?, format: String?, vararg argArray: Any?) = logger.get().warn(marker, format, argArray)
    override fun warn(marker: Marker?, msg: String?, t: Throwable?) = logger.get().warn(marker, msg, t)
    override fun isErrorEnabled(): Boolean = isErrorEnabled
    override fun isErrorEnabled(marker: Marker?): Boolean  = isErrorEnabled(marker)
    override fun error(msg: String?) = logger.get().error(msg)
    override fun error(format: String?, arg: Any?) = logger.get().error(format, arg)
    override fun error(format: String?, arg1: Any?, arg2: Any?) = logger.get().error(format, arg1, arg2)
    override fun error(format: String?, vararg arguments: Any?) = logger.get().error(format, arguments)
    override fun error(msg: String?, t: Throwable?) = logger.get().error(msg, t)
    override fun error(marker: Marker?, msg: String?) = logger.get().error(marker, msg)
    override fun error(marker: Marker?, format: String?, arg: Any?) = logger.get().error(marker, format, arg)
    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = logger.get().error(marker, format, arg1, arg2)
    override fun error(marker: Marker?, format: String?, vararg argArray: Any?) = logger.get().error(marker, format, argArray)
    override fun error(marker: Marker?, msg: String?, t: Throwable?) = logger.get().error(marker, msg, t)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TestSessionLoggerModule {
    @Binds
    abstract fun bindTestSessionLogger(
        testSessionLoggerImpl: TestSessionLoggerImpl
    ): TestSessionLogger
}