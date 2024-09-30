/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

import android.util.Log
import androidx.activity.result.ActivityResultCaller
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface TestCase {
    data class State(
        val preConditions: ConditionCheckManifest,
        val result: TestResult,
        val overall: TestResult
    )

    val id: String
    val description: String
    val instructions: String
    val state: StateFlow<State>

    suspend fun validatePreconditions()

    suspend fun execute()
}

typealias TestCorpus = List<TestCase>

internal interface ActivityLauncherTestCase {
    fun registerActivityLauncher(arc: ActivityResultCaller)
}

internal interface TestResultRecorder {
    fun record(test: TestCase)
}

internal abstract class TestCaseImpl(
    private val preConditions: List<ConditionChecker>
) : TestCase {
    // NOTE: recorder is lazy to break a dependency cycle in TestSessionRepositoryImpl
    @Inject
    lateinit var recorder: Lazy<TestResultRecorder>

    private val _state = MutableStateFlow(
        TestCase.State(
            initialConditionCheckManifest(preConditions),
            TestResult.UNEVALUATED,
            TestResult.UNEVALUATED
        )
    )
    override val state = _state.asStateFlow()

    override suspend fun validatePreconditions() {
        Log.v(TAG, "Test case ID:$id, validating preconditions...")

        val updatedPreconditions = preConditions.map { checker ->
            val result = checker.check()
            ConditionCheckResult(checker.id, checker.description, result)
        }

        _state.update { orig -> orig.copy(preConditions = updatedPreconditions) }
    }

    override suspend fun execute() {
        Log.v(TAG, "Test case ID:$id, running...")
        val executeResult = doExecute()
        Log.v(TAG, "Test case ID:$id, result:${executeResult.name}")

        val overallResult = TestResult.resolve(
            executeResult,
            *_state.value.preConditions.map { p -> p.result }.toTypedArray()
        )

        Log.v(TAG, "Test case ID:$id, overall:${overallResult.name}")

        _state.update { orig ->
            orig.copy(
                result = executeResult,
                overall = overallResult
            )
        }

        recorder.get().record(this)
    }

    protected abstract suspend fun doExecute(): TestResult

    companion object {
        private val TAG = TestCaseImpl::class.simpleName!!

        private fun initialConditionCheckManifest(checkers: List<ConditionChecker>) =
            checkers.map { checker ->
                ConditionCheckResult(checker.id, checker.description, TestResult.UNEVALUATED)
            }
    }
}