/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

import android.util.Log

internal interface ConditionChecker {
    val id: String
    val description: String

    suspend fun check(): TestResult
}

internal abstract class ConditionCheckerImpl : ConditionChecker {
    companion object {
        private val TAG = ConditionCheckerImpl::class.simpleName!!
    }

    override suspend fun check(): TestResult {
        return doCheck().also { testResult ->
            Log.v(TAG, "Check ID:$id, result:${testResult.name}")
        }
    }

    protected abstract suspend fun doCheck(): TestResult
}