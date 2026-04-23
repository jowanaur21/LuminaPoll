# LuminaPoll - Smart Polling for Everyone

LuminaPoll is a versatile Android application designed to facilitate seamless polling and voting in both local (offline) and global (online) environments. Whether you're in a room with no internet or looking to gather opinions worldwide, LuminaPoll provides a robust, real-time solution.

## 🚀 Key Features

### 📡 "Magic" Offline Connectivity
- **Zero-Config Discovery**: Participants don't need to type IP addresses; polls appear automatically using Network Service Discovery (NSD).
- **Host-as-a-Server**: Your phone becomes a real-time server using **Ktor**, allowing polling in areas with zero internet.
- **Smart Fallback**: A 4-digit hex code system that embeds the host's IP for 100% connection reliability.

### ⚡ Real-Time Visualization
- **Live "Lumina" Bars**: Watch public opinion shift in real-time with smooth progress bars as votes are cast.
- **Instant Feedback**: Uses high-speed WebSockets for local mode and Firebase for online, ensuring sub-second latency.

### 🎨 Context-Aware Interface
- **Adaptive Theming**: The app visually shifts between "Deep Sea Blue" (Local) and "Royal Purple" (Online), helping users stay oriented.
- **Full-Screen Focus**: Distraction-free voting layouts and polished progress overlays for a premium feel.

### 🔐 Multi-Layer Security
- **Identity Integrity**: Prevents double-voting by tracking unique Device IDs (Local) or Firebase UIDs (Online).
- **Secure Auth Suite**: Professional-grade login with Google integration and a real-time password strength analyzer.
- **Host Control**: Only the poll creator can end a session early, ensuring full authority over the polling window.

### 🔄 Session Continuity
- **Smart Dashboard**: The app remembers if you have a poll running and provides shortcuts to jump straight back into the action.

## 🛠 Tech Stack
- **Language**: Kotlin
- **Networking**: Ktor (Client & Server), WebSockets
- **Backend**: Firebase (Auth, Firestore, Google Services)
- **Discovery**: mDNS / NSD (Network Service Discovery)
- **Architecture**: MVVM / State-driven UI with Kotlin Coroutines and Flow

## 📦 Getting Started
1. Clone the repository.
2. Add your `google-services.json` to the `app/` directory.
3. Build using Android Studio (Giraffe or newer).
4. For **Local Mode**, ensure devices are on the same WiFi or connected to the host's Hotspot.

---
*LuminaPoll: Illuminating every voice, anywhere.*