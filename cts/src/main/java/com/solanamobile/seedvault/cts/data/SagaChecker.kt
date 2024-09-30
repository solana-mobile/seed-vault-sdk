package com.solanamobile.seedvault.cts.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SagaChecker @Inject constructor() {

    fun isSaga(): Boolean {
        return android.os.Build.MODEL == "Saga"
    }
}