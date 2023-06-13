package com.solanamobile.seedvault.reactnative

import com.facebook.react.bridge.*

// Converts a React ReadableArray into a Kotlin ByteArray.
// Expects ReadableArray to be an Array of ints, where each int represents a byte.
internal fun ReadableArray.toByteArray(): ByteArray =
        ByteArray(size()) { index ->
            getInt(index).toByte()
        }

// Converts a Kotlin ByteArray into a React ReadableArray of ints.
internal fun ByteArray.toWritableArray(): ReadableArray =
    Arguments.createArray().apply {
        forEach {
            this.pushInt(it.toInt())
        }
    }