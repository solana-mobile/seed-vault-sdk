/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.infra

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class CtsTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, CtsTestApplication_Application::class.java.name, context)
    }
}
