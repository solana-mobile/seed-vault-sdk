#!/usr/bin/env bash
#
# Drives :cts instrumentation corpus against adb device.
# Used by .github/workflows/android.yml.
#
#   ANDROID_SERIAL=<serial> bash .github/scripts/run-cts-tests.sh
#
set -euo pipefail

REPORT_DIR=cts/build/reports/androidTests/connected
mkdir -p "$REPORT_DIR"

##############################################################################
# Device readiness and determinism
#
# reactivecircus/android-emulator-runner already does some of this itself
# but still needed for local or remote Genymotion devices.
##############################################################################

echo "adb devices:"
adb devices -l

# Wait 2 minutes for remote device discovery, then fail job
DEADLINE=$((SECONDS + 120))
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    if (( SECONDS > DEADLINE )); then
        echo "device ${ANDROID_SERIAL:-<none>} not ready after 120s" >&2
        adb devices -l >&2
        exit 1
    fi
    sleep 2
done
echo "device ready after $((SECONDS - DEADLINE + 120))s"

# Keep the screen awake for the whole run. If it sleeps between passes the device re-locks and the
# keyguard covers the app, so UiAutomator sees only SystemUI.
adb shell svc power stayon true
adb shell wm dismiss-keyguard || true
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0

# Both CTS flavors share an applicationId but are signed with different keys, so a leftover install
# from an interrupted run fails the next one with INSTALL_FAILED_UPDATE_INCOMPATIBLE. Never happens
# on a freshly provisioned CI device; matters when re-running against a persistent local device.
adb uninstall com.solanamobile.seedvault.cts > /dev/null 2>&1 || true
adb uninstall com.solanamobile.seedvault.cts.test > /dev/null 2>&1 || true

##############################################################################
# Logcat capture (uploaded as a workflow artifact alongside the test reports)
##############################################################################

adb logcat -c
adb logcat >> "$REPORT_DIR/instrumented_tests_logcat.txt" &
trap 'kill %1 2>/dev/null || true' EXIT

##############################################################################
# Test
#
# Ordering is load-bearing: SeedVaultSimulatorSetup wipes simulator seed state
# before each RunCtsTestsOnSimulator pass.
##############################################################################

run_pass() {
  local label=$1 start=$SECONDS
  shift
  echo "::group::$label"
  adb shell wm dismiss-keyguard || true
  "$@"
  echo "::endgroup::"
  echo "$label took $((SECONDS - start))s"
}

run_pass "install SeedVaultSimulator" ./gradlew :SeedVaultSimulator:installDebug

for FLAVOR in Generic Privileged; do
  for CLASS in SeedVaultSimulatorSetup RunCtsTestsOnSimulator; do
    run_pass "$FLAVOR / $CLASS" ./gradlew ":cts:connected${FLAVOR}DebugAndroidTest" \
      "-Pandroid.testInstrumentationRunnerArguments.class=com.solanamobile.seedvault.cts.${CLASS}"
  done
done
