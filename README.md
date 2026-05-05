# LuminaPoll - Smart Polling for Everyone

LuminaPoll is a versatile Android application designed to facilitate seamless polling and voting in both local (offline) and global (online) environments. Whether you're in a room with no internet or looking to gather opinions worldwide, LuminaPoll provides a robust, real-time solution.

## 🚀 Key Features

### 📡 "Magic" Offline Connectivity
- **Zero-Config Discovery**: Participants don't need to type IP addresses; polls appear automatically using Network Service Discovery (NSD).
- **Host-as-a-Server**: Your phone becomes a real-time server using **Ktor**, allowing polling in areas with zero internet.
- **Smart Fallback (4-Digit Code)**: A unique 4-character hex code (e.g., `AE1B`) that embeds the host's IP for 100% connection reliability without manual entry.

### 🌐 Global Online Connectivity
- **Cloud Hosting**: Share your voice globally with Firebase-backed online polls.
- **Secure Access (6-Digit Code)**: Robust 6-character random codes (e.g., `AB12CD`) ensure global uniqueness and ease of sharing.

### ⚡ Real-Time Visualization
- **Live "Lumina" Bars**: Watch public opinion shift in real-time with smooth progress bars as votes are cast.
- **Instant Feedback**: Uses high-speed WebSockets for local mode and Firestore real-time sync for online, ensuring sub-second latency.

### 🎨 Context-Aware Interface
- **Adaptive Theming**: The app visually shifts between "Deep Sea Blue" (Local) and "Royal Purple" (Online).
- **Streamlined Creation**: Just enter your question and options. The app automatically uses your question as the poll title to get you hosting faster.

### 🔐 High Integrity & Security
- **Anti-Double Voting**: Prevents double-counting by tracking unique Device IDs (Local) or Firebase UIDs (Online).
- **Smart Identity Persistence**: If a host accidentally closes the app, re-entering via code automatically restores their Host role and themes.
- **Automated Cleanup**: Local polls vanish 1 hour after ending; Online polls are automatically deleted from the cloud after 24 hours.

### 🔄 Session Continuity
- **Host Shortcuts**: The dashboard automatically detects if you have an active hosting session and provides a one-tap shortcut to your live results.

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