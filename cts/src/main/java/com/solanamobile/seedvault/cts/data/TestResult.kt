/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data

enum class TestResult {
    UNEVALUATED,
    PASS,
    EMPTY,
    FAIL;

    companion object {
        fun resolve(vararg results: TestResult): TestResult {
            return if (results.isEmpty()) {
                EMPTY
            } else if (results.all { r -> r == PASS || r == EMPTY }) {
                PASS
            } else if (results.any { r -> r == FAIL }) {
                FAIL
            } else {
                UNEVALUATED
            }
        }
    }
}