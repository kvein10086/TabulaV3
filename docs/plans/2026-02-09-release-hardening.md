# Release Hardening and Smoothness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate release-blocking edge-case crashes and permission regressions, and remove a high-frequency image preloading performance hotspot that impacts UI smoothness.

**Architecture:** Keep behavior stable while hardening boundary handling in permission/state transitions. Introduce a small permission policy utility with unit tests, then wire MainActivity to it. Remove per-trigger `ImageLoader` construction in DeckScreen and reuse the app loader. Add guardrails around system bucket loading to prevent null races and stale state.

**Tech Stack:** Kotlin, AndroidX, Jetpack Compose, Coil, JUnit4, Gradle

---

### Task 1: Add failing tests for Android 14 media permission policy

**Files:**
- Create: `app/src/test/java/com/tabula/v3/permission/MediaPermissionPolicyTest.kt`
- Create: `app/src/main/java/com/tabula/v3/permission/MediaPermissionPolicy.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun requiredPermissions_sdk34_includesPartialAccessPermission() {
    val permissions = MediaPermissionPolicy.requiredPermissionsForSdk(34)
    assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.tabula.v3.permission.MediaPermissionPolicyTest"`
Expected: FAIL (policy does not exist yet)

**Step 3: Write minimal implementation**

```kotlin
object MediaPermissionPolicy {
    fun requiredPermissionsForSdk(sdkInt: Int): Array<String> = ...
    fun hasMediaReadPermission(sdkInt: Int, isGranted: (String) -> Boolean): Boolean = ...
    fun isAnyRequestedPermissionGranted(result: Map<String, Boolean>): Boolean = ...
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.tabula.v3.permission.MediaPermissionPolicyTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/tabula/v3/permission/MediaPermissionPolicy.kt app/src/test/java/com/tabula/v3/permission/MediaPermissionPolicyTest.kt
git commit -m "test+feat: add media permission policy for android 14 selected photos"
```

### Task 2: Integrate permission policy into MainActivity and manifest

**Files:**
- Modify: `app/src/main/java/com/tabula/v3/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Write/update failing test scope**

```kotlin
@Test
fun hasMediaReadPermission_sdk34_trueWhenOnlySelectedPhotosGranted() { ... }
```

**Step 2: Run targeted test to verify fail-before-change**

Run: `./gradlew.bat testDebugUnitTest --tests "com.tabula.v3.permission.MediaPermissionPolicyTest.hasMediaReadPermission_sdk34_trueWhenOnlySelectedPhotosGranted"`
Expected: FAIL if policy incomplete

**Step 3: Implement minimal integration**

- Switch runtime permission launcher to `RequestMultiplePermissions`.
- On API 34+, request both `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED`.
- Permission check passes if either permission is granted on API 34+.
- Add `READ_MEDIA_VISUAL_USER_SELECTED` to manifest.

**Step 4: Run tests/build**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/tabula/v3/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "fix: support android 14 selected photos permission flow"
```

### Task 3: Harden system bucket loading null/exception boundary

**Files:**
- Modify: `app/src/main/java/com/tabula/v3/MainActivity.kt`

**Step 1: Add failing test (policy-level helper if needed)**

```kotlin
@Test
fun normalizeBucketName_returnsNull_forNullOrBlankInput() { ... }
```

**Step 2: Run targeted tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.tabula.v3.permission.MediaPermissionPolicyTest"`
Expected: FAIL if helper missing

**Step 3: Implement minimal fix**

- Capture `selectedBucketName` into local immutable value before suspension.
- Remove force unwrap `!!`.
- Reset `bucketImages` to empty when bucket is null.
- Wrap load in `runCatching` and keep UI stable on failure.

**Step 4: Run tests/build**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/tabula/v3/MainActivity.kt
git commit -m "fix: prevent bucket load null-race crash in system album view"
```

### Task 4: Remove preloading performance hotspot in DeckScreen

**Files:**
- Modify: `app/src/main/java/com/tabula/v3/ui/screens/DeckScreen.kt`

**Step 1: Add a failing test for extracted preload gate helper (if introduced)**

```kotlin
@Test
fun shouldPreloadNextBatch_returnsTrue_whenRemainingAtOrBelowThreshold() { ... }
```

**Step 2: Run targeted tests (if helper added)**

Run: `./gradlew.bat testDebugUnitTest --tests "*DeckScreen*"`
Expected: FAIL before helper implementation

**Step 3: Implement minimal fix**

- Reuse shared `CoilSetup.getImageLoader(context)` instead of constructing `ImageLoader.Builder(context)` inside `LaunchedEffect`.
- Keep same preloading behavior and threshold; only remove object churn.

**Step 4: Run tests/build**

Run: `./gradlew.bat testDebugUnitTest assembleDebug lintDebug`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/tabula/v3/ui/screens/DeckScreen.kt
git commit -m "perf: reuse shared image loader in cleanup preloading path"
```

### Task 5: Final release verification

**Files:**
- Verify only (no code changes expected)

**Step 1: Run full verification**

Run: `./gradlew.bat testDebugUnitTest assembleDebug lintDebug`
Expected: PASS, no new warnings/errors introduced by this change set

**Step 2: Regression sanity check**

- Verify permission flow behavior on API 34 code path from static logic.
- Verify no `!!` remains in system bucket loading path.
- Verify no per-trigger ImageLoader creation in DeckScreen preloading effect.

**Step 3: Record outcome**

- Summarize modified files and risks
- Note any residual items that still need device-level perf profiling
