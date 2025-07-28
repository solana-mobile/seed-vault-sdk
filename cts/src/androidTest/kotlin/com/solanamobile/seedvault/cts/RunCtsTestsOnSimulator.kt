/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.EventCondition
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.TestResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RunCtsTestsOnSimulator {

    companion object {
        val TAG = RunCtsTestsOnSimulator::class.simpleName

        @Suppress("KotlinConstantConditions")
        private val IS_GENERIC_BUILD = BuildConfig.FLAVOR == "Generic"
        const val TEST_TIMEOUT = 8L * 60L * 1000L // 8 minutes, in ms
        const val STATE_CHANGE_TIMEOUT = 3000L // 3 seconds, in ms
        const val FINISH_BUTTON_TIMEOUT = STATE_CHANGE_TIMEOUT * 10L
        const val SHORT_INTER_ACTION_DELAY = 100L // 100ms

        const val SIMULATOR_PACKAGE_NAME = "com.solanamobile.seedvaultimpl"
    }

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var instrumentation: Instrumentation
    private lateinit var automation: UiAutomation
    private lateinit var device: UiDevice
    private lateinit var packageManager: PackageManager

    @Before
    fun setup() {
        try {
            hiltRule.inject()
        } catch (t: Throwable) {
            Log.e("TEST", t.stackTraceToString())
        }
        context = ApplicationProvider.getApplicationContext()

        instrumentation = InstrumentationRegistry.getInstrumentation()

        // Enable inspection of other windows (such as the IME)
        automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info

        // Launch Test
        device = UiDevice.getInstance(instrumentation)
        packageManager = context.packageManager

        val packageName = ApplicationProvider.getApplicationContext<Context>().packageName
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            .apply {
                if (this == null) {
                    throw IllegalStateException("Couldn't get the LaunchIntent for $packageName")
                }

                // Clear out any previous instances
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        context.startActivity(intent)
    }

    @After
    fun tearDown() {
        context.revokeSelfPermissionOnKill(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT)
    }

    @Test(timeout = TEST_TIMEOUT)
    fun testRunAllTests() {
        runBlocking {
            for (tc in testCases) {
                assertTestId(tc.id)
                device.waitForIdle()

                pressExecuteTestButton(tc.createClickAndWaitCondition())
                device.waitForIdle()

                tc.performAndroidDeviceInteractions()
                device.waitForIdle()

                assertTestPassed()

                pressNextButtonIfPresent()
                device.waitForIdle()
            }

            pressFinishButton()
            device.waitForIdle()

            assertAllTestsPassed()
        }
    }

    private fun assertTestId(expected: String) {
        assertTrue(
            device.waitOrDumpWindowHierarchy(
                Until.findObject(
                    By.res("TestId").hasAncestor(By.res("TestStateScaffold"))
                ), STATE_CHANGE_TIMEOUT
            )?.wait(Until.textEquals(expected), STATE_CHANGE_TIMEOUT) ?: false
        )
    }

    private fun assertTestPassed() {
        assertTrue(
            device.waitOrDumpWindowHierarchy(
                Until.findObject(
                    By.res("TestStateContainer").hasAncestor(By.res("TestStateScaffold"))
                ), STATE_CHANGE_TIMEOUT
            )?.scrollUntil(
                Direction.DOWN, Until.findObject(By.res("TestResult"))
            )?.wait(Until.textEquals(TestResult.PASS.name), STATE_CHANGE_TIMEOUT) ?: false
        )
    }

    private fun assertAllTestsPassed() {
        device.findObject(UiSelector().resourceId("OverallResult").text(TestResult.PASS.name))
            .apply {
                assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
            }
    }

    private fun pressExecuteTestButton(condition: ClickAndWaitCondition) {
        assertTrue(
            device.waitOrDumpWindowHierarchy(
                Until.findObject(
                    By.res("TestStateContainer").hasAncestor(By.res("TestStateScaffold"))
                ), STATE_CHANGE_TIMEOUT
            )?.scrollUntil(
                Direction.UP, Until.findObject(By.res("ValidateAndExecute"))
            )?.clickAndWait(condition, STATE_CHANGE_TIMEOUT) ?: false
        )
    }

    private fun pressNextButtonIfPresent() {
        try {
            device.waitOrDumpWindowHierarchy(
                Until.findObject(
                    By.res("NextTest").hasAncestor(By.res("TestStateScaffold"))
                ), STATE_CHANGE_TIMEOUT
            )?.click()
        } catch (e: StaleObjectException) {
            // Next button might be removed while we are attempting to click it
        }
    }

    private fun pressFinishButton() {
        val finishButton = device.waitOrDumpWindowHierarchy(
            Until.findObject(
                By.res("Finish").hasAncestor(By.res("TestStateScaffold"))
            ), FINISH_BUTTON_TIMEOUT // for some unknown reason, on google_atd devices only, the finish button can take much longer to locate
        )
        assertNotNull(finishButton)
        finishButton!!.click()
    }

    private fun waitForImeHidden(): Boolean {
        return device.wait({
            for (window in automation.windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return@wait false
                }
            }
            return@wait true
        }, STATE_CHANGE_TIMEOUT) ?: false
    }

    private fun UiObject.waitForExistsOrDumpWindowHierarchy(timeout: Long): Boolean {
        return waitForExists(timeout).also { exists ->
            if (!exists) {
                device.dumpWindowHierarchyToLogcat()
            }
        }
    }

    internal interface CtsTestCase {
        val id: String
        fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionLambda { true }
        suspend fun performAndroidDeviceInteractions() = Unit
    }

    internal inner class NoPermissionsContentProviderCheck(override val id: String = "npcpc") : CtsTestCase
    internal inner class Seed12AccountsContentProviderTestCase(override val id: String = "ks12acp") : CtsTestCase
    internal inner class Seed24AccountsContentProviderTestCase(override val id: String = "ks24acp") : CtsTestCase
    internal inner class DeauthorizeSeed12TestCase(override val id: String = "ds12") : CtsTestCase
    internal inner class FetchTooManyPubKeyTestCase(override val id: String = "ftmpk") : CtsTestCase
    internal inner class HasAuthorizedSeedsContentProviderTestCase(override val id: String = "hascp") : CtsTestCase
    internal inner class HasUnauthorizedSeedsContentProviderTestCase(override val id: String = "huascp") : CtsTestCase
    internal inner class ImplementationLimitsContentProviderTestCase(override val id: String = "ilcp") : CtsTestCase
    internal inner class InitialConditionsTestCase(override val id: String = "ic") : CtsTestCase
    internal inner class NoAuthorizedSeedsContentProviderTestCase(override val id: String = "nascp") : CtsTestCase
    internal inner class NoUnauthorizedSeedsContentProviderTestCase(override val id: String = "nuascp") : CtsTestCase
    internal inner class SignMessageRequestsExceedLimitTestCase(override val id: String = "smrel") : CtsTestCase
    internal inner class SignMessageSignaturesExceedLimitTestCase(override val id: String = "smsel") : CtsTestCase
    internal inner class SignTransactionRequestsExceedLimitTestCase(override val id: String = "strel") : CtsTestCase
    internal inner class SignTransactionSignaturesExceedLimitTestCase(override val id: String = "stsel") : CtsTestCase
    internal inner class DeauthorizeSeed24TestCase(override val id: String = "ds24") : CtsTestCase
    internal inner class CannotShowSeedSettingsTestCase(override val id: String = "csss") : CtsTestCase

    internal inner class AcquireSeedVaultPermissionTestCase : CtsTestCase {
        override val id = "asvp"

        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            val allowPermission = device.waitOrDumpWindowHierarchy(
                Until.findObject(
                    By.text(Pattern.compile("allow", Pattern.CASE_INSENSITIVE))
                ), STATE_CHANGE_TIMEOUT
            )
            assertNotNull("$id: Did not find the allow permission button", allowPermission)
            allowPermission!!.click()
        }
    }
    internal inner class AcquireSeedVaultPrivilegedPermissionTestCase(override val id: String = "asvpp") : CtsTestCase

    internal abstract inner class ImportSeedTestCase(
        private val seedName: String,
        private val seedPin: String,
        private val seeds: Array<String>,
        private val enableBiometrics: Boolean
    ) : CtsTestCase {
        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            UiSelector().resourceId("SeedName").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    click()
                }
                device.pressKeyCodeSequence(seedName)
                assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            }

            device.waitForIdle()

            UiSelector().resourceId("SeedPin").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    // No need to click() here; already has focus from previous KEYCODE_TAB input
                }
                device.pressKeyCodeSequence(seedPin)
                assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            }

            waitForImeHidden()
            device.waitForIdle()

            UiSelector().resourceId("EnableBiometrics").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    if (enableBiometrics) {
                        click()
                    }
                }
            }

            device.waitForIdle()

            UiSelector().resourceId("PhraseLength${seeds.size}").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    click()
                }
            }

            device.waitForIdle()

            seeds.forEachIndexed { index, seedWord ->
                UiSelector().resourceId("SeedPhrase#$index").let { selector ->
                    device.findObject(selector).apply {
                        assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                        if (index == 0) {
                            click()
                        } else {
                            // No need to click() here; already has focus from previous KEYCODE_TAB input
                        }
                    }
                    device.pressKeyCodeSequence(seedWord)
                    assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                }
            }

            waitForImeHidden()
            device.waitForIdle()

            device.findObject(UiSelector().resourceId("Save")).apply {
                assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                assertTrue(clickAndWaitForNewWindow(2 * STATE_CHANGE_TIMEOUT))
            }
        }
    }

    internal inner class ImportSeed12TestCase(override val id: String = "is12") :
        ImportSeedTestCase(
            "Test 1", "123456", arrayOf(
                "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "egg"
            ), true
        )
    internal inner class ImportSeed24TestCase(override val id: String = "is24") :
        ImportSeedTestCase(
            "Test 2", "654321", arrayOf(
                "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox",
                "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox",
                "fox", "fox", "fox", "fox", "fox", "fox", "fox", "oven"
            ), false
        )

    internal abstract inner class AuthorizeWithBiometricsTestCase : CtsTestCase {
        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            assertTrue(
                device.waitOrDumpWindowHierarchy(
                    Until.findObject(By.res("AuthorizeFingerprint")),
                    STATE_CHANGE_TIMEOUT
                )?.clickAndWait(Until.newWindow(), STATE_CHANGE_TIMEOUT) ?: false
            )
        }
    }

    internal inner class PermissionedAccountFetchPubKeysGenericTestCase(override val id: String = "pafpktc") : AuthorizeWithBiometricsTestCase()
    internal inner class PermissionedAccountFetchPubKeysPrivilegedTestCase(override val id: String = "pafpktc") : CtsTestCase
    internal inner class Fetch1PubKeyTestCase(override val id: String = "f1pk") : AuthorizeWithBiometricsTestCase()
    internal inner class FetchMaxPubKeyTestCase(override val id: String = "fmaxpk") : AuthorizeWithBiometricsTestCase()
    internal inner class ReauthorizeSeed12TestCase(override val id: String = "rs12") : AuthorizeWithBiometricsTestCase()
    internal inner class Sign1MessageWith1SignatureTestCase(override val id: String = "s1m1s") : AuthorizeWithBiometricsTestCase()
    internal inner class Sign1TransactionWith1SignatureTestCase(override val id: String = "s1t1s") : AuthorizeWithBiometricsTestCase()
    internal inner class SignMaxMessageWithMaxSignatureBip44TestCase(override val id: String = "smaxmmaxsb44") : AuthorizeWithBiometricsTestCase()
    internal inner class SignMaxMessageWithMaxSignatureTestCase(override val id: String = "smaxmmaxs") : AuthorizeWithBiometricsTestCase()
    internal inner class SignMaxTransactionWithMaxSignatureBip44TestCase(override val id: String = "smaxtmaxsb44") : AuthorizeWithBiometricsTestCase()
    internal inner class SignMaxTransactionWithMaxSignatureTestCase(override val id: String = "smaxtmaxs") : AuthorizeWithBiometricsTestCase()

    internal inner class CreateNewSeedTestCase : CtsTestCase {
        override val id = "cns"

        private val seedName = "Test 3"
        private val seedPin = "000000"
        private val enableBiometrics = true

        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            UiSelector().resourceId("SeedName").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    click()
                }
                device.pressKeyCodeSequence(seedName)
                assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            }

            device.waitForIdle()

            UiSelector().resourceId("SeedPin").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    // No need to click() here; already has focus from previous KEYCODE_TAB input
                }
                device.pressKeyCodeSequence(seedPin)
                assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            }

            waitForImeHidden()
            device.waitForIdle()

            UiSelector().resourceId("EnableBiometrics").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    if (enableBiometrics) {
                        click()
                    }
                }
            }

            device.waitForIdle()

            device.findObject(UiSelector().resourceId("Save")).apply {
                assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                assertTrue(clickAndWaitForNewWindow(STATE_CHANGE_TIMEOUT))
            }
        }
    }

    internal abstract inner class WaitForNewWindowAndNavigateBackTestCase : CtsTestCase {
        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            device.pressBack()
        }
    }

    internal inner class DenySignMessageTestCase(override val id: String = "dsm") : WaitForNewWindowAndNavigateBackTestCase()
    internal inner class DenySignTransactionTestCase(override val id: String = "dst") : WaitForNewWindowAndNavigateBackTestCase()
    internal inner class ShowSeedSettingsTestCase(override val id: String = "sss") : WaitForNewWindowAndNavigateBackTestCase()

    internal abstract inner class DenyAuthorizationWithIncorrectPinTestCase : CtsTestCase {
        override fun createClickAndWaitCondition(): ClickAndWaitCondition = ClickAndWaitConditionEvent(Until.newWindow())

        override suspend fun performAndroidDeviceInteractions() {
            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR == "Privileged") {
                delay(SHORT_INTER_ACTION_DELAY)
                for (i in 1..3) {
                    // Click outside alert dialog.
                    device.click(10, 100)
                    delay(SHORT_INTER_ACTION_DELAY)
                    device.waitForIdle()
                }
            } else {
                assertNotNull(device.waitOrDumpWindowHierarchy(
                    Until.findObject(By.res("DenyFingerprint")),
                    STATE_CHANGE_TIMEOUT
                )?.apply {
                    for (i in 1..3) {
                        click()
                        delay(SHORT_INTER_ACTION_DELAY)
                        device.waitForIdle()
                    }
                })
            }

            // No device.waitForIdle() here; previous loop includes it

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "Privileged") {
                assertTrue(
                    device.waitOrDumpWindowHierarchy(
                        Until.findObject(By.res("UsePin")),
                        2 * STATE_CHANGE_TIMEOUT
                    )?.clickAndWait(Until.newWindow(), STATE_CHANGE_TIMEOUT) ?: false
                )

                device.waitForIdle()
            }

            assertNotNull(device.waitOrDumpWindowHierarchy(
                Until.findObject(By.res("PinEntryField")),
                STATE_CHANGE_TIMEOUT
            )?.apply {
                click()
                device.pressKeyCodeSequence("000000") // not the valid PIN
            })

            device.waitForIdle()

            assertNotNull(
                device.waitOrDumpWindowHierarchy(
                    Until.findObject(By.text(context.getString(android.R.string.ok))),
                    STATE_CHANGE_TIMEOUT
                )?.apply {
                    for (i in 1..5) {
                        click()
                        device.waitForIdle()
                    }
                }
            )
        }
    }

    internal inner class IncorrectPinSignMessageFailureTestCase(override val id: String = "ipsmf") : DenyAuthorizationWithIncorrectPinTestCase()
    internal inner class IncorrectPinSignTransactionFailureTestCase(override val id: String = "ipstf") : DenyAuthorizationWithIncorrectPinTestCase()

    internal inner class RenameExistingSeedTestCase(override val id: String = "res") : CtsTestCase {
        override suspend fun performAndroidDeviceInteractions() {
            assertTrue(device.performActionAndWait({
                val intent =
                    context.packageManager.getLaunchIntentForPackage(SIMULATOR_PACKAGE_NAME).apply {
                        if (this == null) {
                            throw IllegalStateException("Couldn't get the LaunchIntent for $SIMULATOR_PACKAGE_NAME")
                        }

                        // Clear out any previous instances
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                context.startActivity(intent)
            }, Until.newWindow(), STATE_CHANGE_TIMEOUT))

            device.waitForIdle()

            UiSelector().text("Test 3").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    assertTrue(clickAndWaitForNewWindow(STATE_CHANGE_TIMEOUT))
                }
            }

            device.waitForIdle()

            UiSelector().resourceId("SeedName").let { selector ->
                device.findObject(selector).apply {
                    assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                    click()
                    clearTextField()
                }
                device.pressKeyCodeSequence("Renamed Seed")
                assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            }

            waitForImeHidden()
            device.waitForIdle()

            device.findObject(UiSelector().resourceId("Save")).apply {
                assertTrue(waitForExistsOrDumpWindowHierarchy(STATE_CHANGE_TIMEOUT))
                assertTrue(clickAndWaitForNewWindow(STATE_CHANGE_TIMEOUT))
            }

            device.waitForIdle()

            device.pressBack() // dismiss the Seed Vault Simulator window and return to CTS app

            device.waitForIdle()

            assertNotNull(
                device.waitOrDumpWindowHierarchy(
                    Until.findObject(By.res("ExternalActionComplete")), STATE_CHANGE_TIMEOUT
                )?.click()
            )
        }
    }

    private val testCases = listOfNotNull(
        NoPermissionsContentProviderCheck().takeIf { IS_GENERIC_BUILD },
        AcquireSeedVaultPrivilegedPermissionTestCase().takeIf { IS_GENERIC_BUILD },
        AcquireSeedVaultPermissionTestCase().takeIf { IS_GENERIC_BUILD },
        InitialConditionsTestCase(),
        NoUnauthorizedSeedsContentProviderTestCase(),
        NoAuthorizedSeedsContentProviderTestCase(),
        ImportSeed12TestCase(),
        PermissionedAccountFetchPubKeysGenericTestCase().takeIf { IS_GENERIC_BUILD },
        PermissionedAccountFetchPubKeysPrivilegedTestCase().takeIf { !IS_GENERIC_BUILD },
        Fetch1PubKeyTestCase(),
        FetchMaxPubKeyTestCase(),
        FetchTooManyPubKeyTestCase(),
        Sign1TransactionWith1SignatureTestCase(),
        SignMaxTransactionWithMaxSignatureTestCase(),
        SignMaxTransactionWithMaxSignatureBip44TestCase(),
        SignTransactionRequestsExceedLimitTestCase(),
        SignTransactionSignaturesExceedLimitTestCase(),
        DenySignTransactionTestCase(),
        IncorrectPinSignTransactionFailureTestCase(),
        Sign1MessageWith1SignatureTestCase(),
        SignMaxMessageWithMaxSignatureTestCase(),
        SignMaxMessageWithMaxSignatureBip44TestCase(),
        SignMessageRequestsExceedLimitTestCase(),
        SignMessageSignaturesExceedLimitTestCase(),
        DenySignMessageTestCase(),
        IncorrectPinSignMessageFailureTestCase(),
        CannotShowSeedSettingsTestCase().takeIf { IS_GENERIC_BUILD },
        ShowSeedSettingsTestCase().takeIf { !IS_GENERIC_BUILD },
        DeauthorizeSeed12TestCase().takeIf { IS_GENERIC_BUILD },
        HasUnauthorizedSeedsContentProviderTestCase().takeIf { IS_GENERIC_BUILD },
        ReauthorizeSeed12TestCase().takeIf { IS_GENERIC_BUILD },
        ImportSeed24TestCase(),
        HasAuthorizedSeedsContentProviderTestCase(),
        Seed12AccountsContentProviderTestCase(),
        Seed24AccountsContentProviderTestCase(),
        CreateNewSeedTestCase(),
        ImplementationLimitsContentProviderTestCase(),
        DeauthorizeSeed24TestCase().takeIf { IS_GENERIC_BUILD },
        RenameExistingSeedTestCase(),
    )
}

fun UiDevice.pressKeyCodeSequence(str: String) {
    str.forEach { c ->
        val symbolicName = when (c) {
            ' ' -> "KEYCODE_SPACE"
            else -> "KEYCODE_${c.uppercase()}"
        }
        pressKeyCode(
            KeyEvent.keyCodeFromString(symbolicName),
            if (c.isUpperCase()) KeyEvent.META_SHIFT_ON else 0
        )
    }
}

internal interface ClickAndWaitCondition {
    fun performClickAndWait(obj: UiObject2, timeout: Long): Boolean
}

internal class ClickAndWaitConditionEvent(
    private val condition: EventCondition<Boolean>
) : ClickAndWaitCondition {
    override fun performClickAndWait(obj: UiObject2, timeout: Long): Boolean =
        obj.clickAndWait(condition, timeout)
}

internal class ClickAndWaitConditionLambda(
    private val condition: (UiObject2) -> Boolean
) : ClickAndWaitCondition {
    override fun performClickAndWait(obj: UiObject2, timeout: Long): Boolean {
        obj.click()
        return obj.wait(condition, timeout)
    }
}

internal fun UiObject2.clickAndWait(condition: ClickAndWaitCondition, timeout: Long): Boolean =
    condition.performClickAndWait(this, timeout)

internal fun <U> UiDevice.waitOrDumpWindowHierarchy(condition: SearchCondition<U>, timeout: Long): U? {
    return wait(condition, timeout) ?: run {
        dumpWindowHierarchyToLogcat()
        null
    }
}

internal fun UiDevice.dumpWindowHierarchyToLogcat() {
    ByteArrayOutputStream().use { os ->
        dumpWindowHierarchy(os)
        val str = String(os.toByteArray(), Charsets.UTF_8)
        Log.d(RunCtsTestsOnSimulator.TAG, "Dumping window hierarchy")
        for (line in str.lines()) {
            Log.d(RunCtsTestsOnSimulator.TAG, line)
        }
    }
}