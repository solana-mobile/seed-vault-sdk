/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

data class ConditionCheckResult(
    val id: String,
    val description: String,
    val result: TestResult
)

typealias ConditionCheckManifest = List<ConditionCheckResult>