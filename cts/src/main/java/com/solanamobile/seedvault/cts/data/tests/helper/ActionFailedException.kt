/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests.helper

class ActionFailedException(actionName: String, val errorCode: Int) :
    Exception("$actionName failed with result=$errorCode")


