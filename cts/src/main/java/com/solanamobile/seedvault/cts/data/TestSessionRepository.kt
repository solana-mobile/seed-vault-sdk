/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

import android.util.Log
import androidx.activity.result.ActivityResultCaller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface TestSessionRepository {
    val test: StateFlow<TestCase>

    val testCorpus: TestCorpus

    fun registerActivityResultLaunchers(arc: ActivityResultCaller)

    fun hasNext(): Boolean

    fun hasPrevious(): Boolean

    fun advance()

    fun reverse()

    fun end()
}

@ActivityRetainedScoped
internal class TestSessionRepositoryImpl @Inject constructor(
    override val testCorpus: @JvmSuppressWildcards TestCorpus,
    private val logger: TestSessionLogger
) : TestSessionRepository, TestResultRecorder {
    companion object {
        private val TAG = TestSessionRepositoryImpl::class.simpleName!!
    }

    private var activeIndex = 0

    private val _test = MutableStateFlow(testCorpus[activeIndex])
    override val test = _test.asStateFlow()

    init {
        logger.info("Initializing test session")
    }

    override fun registerActivityResultLaunchers(arc: ActivityResultCaller) {
        Log.d(TAG, "Registering activity result launchers")

        testCorpus.forEach { testCase ->
            when (testCase) {
                is ActivityLauncherTestCase -> testCase.registerActivityLauncher(arc)
                else -> Unit
            }
        }
    }

    override fun hasNext(): Boolean {
        return activeIndex + 1 < testCorpus.size
    }

    override fun hasPrevious(): Boolean {
        return activeIndex > 0
    }

    override fun advance() {
        if (activeIndex + 1 < testCorpus.size) {
            _test.update {
                activeIndex += 1
                testCorpus[activeIndex]
            }
        }
    }

    override fun reverse() {
        if (activeIndex > 0) {
            _test.update {
                activeIndex -= 1
                testCorpus[activeIndex]
            }
        }
    }

    override fun end() {
        logger.info("Tests Terminated.")
    }

    override fun record(test: TestCase) {
        val result = test.state.value
        logger.info("Test case ID:${test.id}, R:${result.result.name} O:${result.overall.name}")
        for (preConditions in result.preConditions) {
            logger.info("  [PRE] ID:${preConditions.id}  R:${preConditions.result.name}")
        }
    }
}

@Module
@InstallIn(ActivityComponent::class)
internal abstract class TestSessionRepositoryModule {
    @Binds
    abstract fun bindTestSessionRepository(
        testSessionRepositoryImpl: TestSessionRepositoryImpl
    ): TestSessionRepository
}

@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class TestResultRecorderModule {
    @Binds
    abstract fun bindTestResultRecorder(
        testSessionRepositoryImpl: TestSessionRepositoryImpl
    ): TestResultRecorder
}
