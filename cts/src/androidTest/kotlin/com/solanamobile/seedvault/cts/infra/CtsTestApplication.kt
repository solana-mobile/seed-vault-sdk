/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.infra

import android.app.Application
import dagger.hilt.android.testing.CustomTestApplication

@CustomTestApplication(CtsTestApplication::class)
open class CtsTestApplication : Application() {
}