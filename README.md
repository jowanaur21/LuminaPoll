# LuminaPoll - Smart Polling for Everyone

LuminaPoll is a versatile Android application designed to facilitate seamless polling and voting in both local (offline) and global (online) environments. Whether you're in a room with no internet or looking to gather opinions worldwide, LuminaPoll provides a robust, real-time solution.

## 🚀 Key Features

### 📡 "Magic" Offline Connectivity
- **Zero-Config Discovery**: Participants don't need to type IP addresses; all nearby polls appear automatically using a robust **NSD Resolve Queue** that handles multiple hosts simultaneously.
- **Host-as-a-Server**: Your phone becomes a real-time server using **Ktor**, allowing polling in areas with zero internet.
- **Hotspot Support**: Optimized for Android hotspots with automatic gateway detection for 100% connectivity when the host is also the Wi-Fi provider.
- **Smart Fallback (4-Digit Code)**: A unique 4-character hex code (e.g., `AE1B`) that embeds the host's IP for reliable connection without manual discovery.

### 🌐 Global Online Connectivity
- **Cloud Hosting**: Share your voice globally with Firebase-backed online polls.
- **Secure Access (6-Digit Code)**: Robust 6-character random codes (e.g., `AB12CD`) ensure global uniqueness and ease of sharing.
- **Clock Drift Resilience**: Intelligent 5-minute buffers ensure voters can join and vote even if their device clocks aren't perfectly synchronized with the host.

### 🎨 Context-Aware Interface
- **Adaptive Theming**: The app visually shifts between "Deep Sea Blue" (Local) and "Royal Purple" (Online).
- **Refined Voting UI**: Clean, distraction-free voting with green checkmark indicators and neutral backgrounds for maximum clarity.

### 🔐 High Integrity & Persistence
- **Anti-Double Voting**: Prevents double-counting by tracking unique Device IDs (Local) or Firebase UIDs (Online).
- **Result Persistence**: Polls and results are **never automatically deleted**. Even after a poll ends, the data remains available for viewing as long as the host maintains the session (Local) or as a permanent record in the cloud (Online).
- **Host Protection**: Only the original host can officially end a poll or update its global status, preventing unauthorized state changes.

### 🔄 Session Security
- **Explicit Entry**: The "Enter Code" screen always requires a manual code entry and search, preventing accidental auto-redirections to old sessions and ensuring user intent.

## 🛠 Tech Stack
- **Language**: Kotlin
- **Networking**: Ktor (Client & Server), WebSockets
- **Backend**: Firebase (Auth, Firestore, Google Services)
- **Discovery**: mDNS / NSD (Network Service Discovery)
- **Architecture**: State-driven UI with Kotlin Coroutines and Flow

## 📖 Developer Documentation
For a deep dive into the code architecture, real-time sync logic, and debugging tips, please refer to the **[DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md)**.

## 📦 Getting Started
1. Clone the repository.
2. Add your `google-services.json` to the `app/` directory.
3. Build using Android Studio (Ladybug or newer).
4. For **Local Mode**, ensure devices are on the same WiFi or connected to the host's Hotspot.

---
*LuminaPoll: Illuminating every voice, anywhere.*