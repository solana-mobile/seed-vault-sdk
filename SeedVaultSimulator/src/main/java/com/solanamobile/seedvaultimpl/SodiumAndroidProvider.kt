/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.nio.charset.StandardCharsets

@Module
@InstallIn(SingletonComponent::class)
class SodiumAndroidProvider {
    @Provides
    fun provideLazySodiumAndroid() : LazySodiumAndroid {
        return LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)
    }
}
