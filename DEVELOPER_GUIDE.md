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
    *   `NsdHelper.kt` broadcasts the server's presence over the local network using mDNS (Bonjour/ZeroConf) so other phones can discover it.
*   **Joining:**
    *   `ScanPollsActivity.kt` uses `NsdHelper` to scan the network for active polls.
    *   Alternatively, `EnterCodeActivity.kt` parses the 4-digit code (which secretly contains the last byte of the host's IP address) to connect directly.
    *   `KtorLocalClient.kt` connects to the host via WebSockets.
*   **Real-time updates:** Votes are sent via WebSockets. The server updates its `StateFlow`, which broadcasts the new state back to all connected clients instantly.

### 2. Online Polling Flow (Firebase)
This flow uses Google's Firebase Firestore for global connectivity.
*   **Manager:** `OnlinePollManager.kt` is the single source of truth for online operations.
*   **Real-time updates:** It uses Firestore's `addSnapshotListener`. Whenever a vote is cast in the database, Firebase pushes the update to the app, updating the `StateFlow`.
*   **Voting:** Uses `db.runTransaction` to ensure that even if 100 people vote at the exact same millisecond, the counts remain perfectly accurate.

---

## 🐛 Debugging Guide (How to fix things)

If something breaks, here is where you should look:

### Issue: "Local Poll isn't showing up in the Scan list"
1.  **Check:** Are both devices on the *exact same* Wi-Fi network? (Some public Wi-Fi networks block device-to-device communication).
2.  **Debug file:** Look in `NsdHelper.kt`. Add `Log.d("NSD", "Discovery failed")` inside the error callbacks.
3.  **Fallback:** Tell the user to use the 4-digit manual code.

### Issue: "Cannot connect to Local Poll via 4-digit code"
1.  **Check:** Does the Joiner's IP subnet match the Host's? The 4-digit code relies on `NetworkUtils.getLocalIpAddress()`. If the host is on `192.168.1.5` and the joiner is on a different subnet, the code trick won't work.
2.  **Debug file:** `EnterCodeActivity.kt` -> `joinLocalPoll()`. Check if `hostIpPrefix` is being calculated correctly.

### Issue: "Failed to Create/Join Online Poll"
1.  **Check:** Firebase Rules. If the user isn't logged in, or if the Firestore rules block writes, it will fail.
2.  **Debug file:** `OnlinePollManager.kt`. Look at the `addOnFailureListener` blocks. You can add `Log.e("FirebaseError", it.message)` to print the exact Firebase error to Android Studio's Logcat.

### Issue: "Participant count is wrong or inflating"
1.  **Check:** Unique tracking logic.
2.  **Debug files:** 
    *   *Local:* `KtorLocalServer.kt` inside `handleMessage(message: PollMessage?)`. Look for `message.deviceId`.
    *   *Online:* `OnlinePollManager.kt` inside `joinPoll`. Look for the `transaction` block where it checks `poll.participantIds.contains(userId)`.

### Issue: "Themes are mixed up (Blue screen on an Online poll)"
1.  **Check:** The `EXTRA_MODE` and `EXTRA_ROLE` intent flags.
2.  **Debug file:** `BaseActivity.kt`. This class intercepts `onCreate` and applies the theme based on those flags. If a screen looks wrong, it means the Activity that launched it forgot to pass `putExtra("EXTRA_MODE", mode)`.

---

## 🧹 Maintenance Tips

*   **Adding new strings:** Try not to hardcode text (like `"Vote submitted successfully!"`) in the `.kt` files. Moving them to `app/src/main/res/values/strings.xml` and calling `getString(R.string.vote_success)` makes the app easier to translate in the future.
*   **Updating Colors:** If you want to change the "Online Purple" or "Local Blue", go to `res/values/colors.xml` and `res/values/themes.xml`. The app uses attributes (`?attr/colorModePrimary`) so changing it in `themes.xml` changes it *everywhere* instantly.
*   **Foreground Service:** `PollForegroundService.kt` keeps the local server alive when the host minimizes the app. If Android kills the server aggressively, ensure this service is requesting the correct permissions in `AndroidManifest.xml`.
