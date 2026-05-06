# LuminaPoll Developer & Debugging Guide

Welcome to the developer documentation for LuminaPoll! This guide is designed to help you understand the app's architecture, how the core features work, and where to look when you need to debug or maintain the code.

## 📁 Package Structure (Package-by-Feature)

The app is structured by **feature** rather than by layer. This makes it easier to find all the files related to a specific screen or functionality.

`app/src/main/java/company/luminapoll/`
*   **`core/`**: The foundation of the app.
    *   **`base/`**: Contains `BaseActivity.kt` (handles edge-to-edge UI, keyboard hiding, and mode-based themes).
    *   **`network/`**: The most critical folder. Contains all networking logic (`KtorLocalServer`, `OnlinePollManager`, `PollModels`).
    *   **`utils/`**: Helper classes like `NsdHelper` (for local discovery), `NetworkUtils`, and `DeviceIdProvider`.
*   **`features/`**: The UI and screen logic.
    *   **`auth/`**: Login, Register, Forgot Password flows.
    *   **`dashboard/`**: The Home screen (mode selection) and Dashboard (Host/Join selection).
    *   **`local/`**: Specific to local mode (e.g., `ScanPollsActivity`).
    *   **`poll/`**: Everything related to the polling lifecycle (`CreatePollActivity`, `VoteActivity`, `LivePollActivity`, `PollResultActivity`).

---

## 🧠 Core Architecture & Flows

The app relies heavily on **Kotlin Flows** (`StateFlow` and `SharedFlow`) and **Coroutines** for asynchronous programming and UI updates.

### 1. Local Polling Flow (Offline)
This is the most complex part of the app. It turns one phone into a server and others into clients.
*   **Hosting:**
    *   `KtorLocalServer.kt` starts an embedded HTTP/WebSocket server on the host's phone.
    *   `NsdHelper.kt` broadcasts the server's presence.
*   **Joining:**
    *   `ScanPollsActivity.kt` uses a robust **Resolve Queue** in `NsdHelper.kt` to scan the network. This queue solves the common Android NSD bug where multiple simultaneous resolutions fail.
    *   `EnterCodeActivity.kt` uses the 4-digit code and a **Gateway IP Fallback** to ensure connectivity even when the host is a Wi-Fi Hotspot.
*   **Persistence:** Local results persist as long as the server is running. Automatic shutdown on expiry has been disabled to allow voters to view results at their own pace.

### 2. Online Polling Flow (Firebase)
This flow uses Google's Firebase Firestore for global connectivity.
*   **Manager:** `OnlinePollManager.kt` is the single source of truth.
*   **Clock Drift Handling:** The manager implements a 5-minute grace period for voting and joining, preventing "Poll Ended" errors caused by unsynchronized device clocks.
*   **Host-Only Authority:** Only the host UID is authorized to update a poll's status to `ENDED` in Firestore, preventing voters with fast clocks from accidentally closing the poll for everyone.

---

## 🐛 Debugging Guide (How to fix things)

### Issue: "Only one poll shows up in the Scan list"
1.  **Check:** `NsdHelper.kt` resolve queue. If the `isResolving` flag gets stuck, discovery will stall.
2.  **Fix:** Ensure `stopDiscovery()` is called to reset the queue state.

### Issue: "Immediate 'Poll Ended' message on Online polls"
1.  **Check:** Device clock. If the voter's clock is significantly ahead of the host's, they might see the poll as ended locally.
2.  **Fix:** Check `OnlinePollManager.kt` buffers. We currently allow a 5-minute drift.

### Issue: "Radio buttons in VoteActivity look wrong"
1.  **Check:** `VoteActivity.kt` -> `selectOption()`. 
2.  **Logic:** Selection should only update `iv_radio_check` visibility and tint (Green), while keeping `v_radio_container` and the item background neutral.

### Issue: "Unexpected redirection to an old poll"
1.  **Check:** `EnterCodeActivity.kt` -> `isSearching` flag. 
2.  **Fix:** Ensure redirections only trigger when `isSearching` is true, preventing auto-redirection from lingering client states on activity start.

---

## 🧹 Maintenance Tips

*   **Adding new strings:** Try not to hardcode text (like `"Vote submitted successfully!"`) in the `.kt` files. Moving them to `app/src/main/res/values/strings.xml` and calling `getString(R.string.vote_success)` makes the app easier to translate in the future.
*   **Updating Colors:** If you want to change the "Online Purple" or "Local Blue", go to `res/values/colors.xml` and `res/values/themes.xml`. The app uses attributes (`?attr/colorModePrimary`) so changing it in `themes.xml` changes it *everywhere* instantly.
*   **Foreground Service:** `PollForegroundService.kt` keeps the local server alive when the host minimizes the app. If Android kills the server aggressively, ensure this service is requesting the correct permissions in `AndroidManifest.xml`.
