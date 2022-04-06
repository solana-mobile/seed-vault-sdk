/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.data.proto

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

private object SeedCollectionSerializer : Serializer<SeedCollection> {
    override val defaultValue: SeedCollection = SeedCollection.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): SeedCollection {
        try {
            return SeedCollection.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto", exception)
        }
    }

    override suspend fun writeTo(t: SeedCollection, output: OutputStream) = t.writeTo(output)
}

val Context.seedCollectionDataStore: DataStore<SeedCollection> by dataStore(
    fileName = "seed_collection.pb", serializer = SeedCollectionSerializer)