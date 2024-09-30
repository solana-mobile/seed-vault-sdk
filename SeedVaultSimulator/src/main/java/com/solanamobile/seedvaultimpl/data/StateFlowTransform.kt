/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.data

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * A simple class to transform a StateFlow. It does not perform any caching - every access of an
 * element of type R invokes the provided transformation on an element of type T.
 */
internal class StateFlowTransform<T, R>(
    private val wrappedStateFlow: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {
    override val replayCache: List<R>
        get() = wrappedStateFlow.replayCache.map(transform)
    override val value: R
        get() = transform(wrappedStateFlow.value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        wrappedStateFlow.map(transform).collect(collector)
        throw IllegalStateException("Wrapped state flow returned from collect(); should never occur")
    }
}

fun <T, R> StateFlow<T>.transform(transform: (T) -> R): StateFlow<R> =
    StateFlowTransform(this, transform)