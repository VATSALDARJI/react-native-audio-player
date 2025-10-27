# 🎧 @vatsaldarji/react-native-audio-player

A lightweight and flexible **React Native Audio Player** built from scratch — no builder tools like Bob, fully customizable, and supports both local and streaming URLs.

Provides an easy-to-use interface to play, pause, seek, and monitor audio progress — with support for waveform extraction (coming soon).

---

## 🚀 Features

- ✅ Simple API for play, pause, stop, seek
- 🎚 Volume and rate control
- 🔁 Buffering & position updates
- 🎛 Supports multiple player instances
- ⚡ Works with streaming audio URLs
- 🎵 (Coming soon) Real-time waveform extraction via `MediaCodec`

---

## 📦 Installation

```bash
# Using npm
npm install @vatsaldarji/react-native-audio-player

# Or using yarn
yarn add @vatsaldarji/react-native-audio-player
```

---

## 🔧 Setup

### 1. Android

In your `android/settings.gradle` or `android/settings.gradle.kts`, make sure your library is linked properly (auto-linking usually handles this):

```gradle
include ':react-native-audio-player'
project(':react-native-audio-player').projectDir = new File(rootProject.projectDir, '../node_modules/@vatsaldarji/react-native-audio-player/android')
```

Then rebuild your app:

```bash
cd android && ./gradlew clean && cd ..
yarn android
```

---

## 🧠 Usage Example

Here’s how to use the player inside your React Native screen:

```tsx
import React, { useState } from "react";
import { View, Text, Button } from "react-native";
import AudioPlayer, {
  useProgress,
} from "@vatsaldarji/react-native-audio-player";

const CustomScreen = () => {
  const playerId = "custom-player";
  const [isPlaying, setIsPlaying] = useState(false);
  const [audioUrl] = useState(
    "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3"
  );
  const progress = useProgress(playerId);

  const handlePlay = async () => {
    await AudioPlayer.initialize(playerId);
    await AudioPlayer.load(playerId, audioUrl);
    await AudioPlayer.play(playerId);
    setIsPlaying(true);
  };

  const handlePause = async () => {
    await AudioPlayer.pause(playerId);
    setIsPlaying(false);
  };

  return (
    <View style={{ padding: 20 }}>
      <Text>🎵 Audio Player Demo</Text>

      <Button
        title={isPlaying ? "Pause" : "Play"}
        onPress={isPlaying ? handlePause : handlePlay}
      />

      <Text>Position: {Math.floor(progress.position / 1000)}s</Text>
    </View>
  );
};

export default CustomScreen;
```

---

## 🧩 API Reference

### 🎛 Methods

| Method                                        | Description                   |
| --------------------------------------------- | ----------------------------- |
| `initialize(playerId: string)`                | Initializes a player instance |
| `load(playerId: string, url: string)`         | Loads an audio URL            |
| `play(playerId: string)`                      | Starts playback               |
| `pause(playerId: string)`                     | Pauses playback               |
| `stop(playerId: string)`                      | Stops playback                |
| `seekTo(playerId: string, position: number)`  | Seeks to a specific position  |
| `setVolume(playerId: string, volume: number)` | Sets player volume (0.0–1.0)  |
| `setRate(playerId: string, rate: number)`     | Adjusts playback rate         |
| `release(playerId: string)`                   | Releases the player resources |

---

### 🎧 Hooks

#### `useProgress(playerId: string)`

Returns current progress values for the given player.

```ts
const { position, duration, buffered } = useProgress(playerId);
```

| Field      | Description                    |
| ---------- | ------------------------------ |
| `position` | Current playback position (ms) |
| `duration` | Total duration (ms)            |
| `buffered` | Buffered position (ms)         |

---

## 🧰 Example Integration

See `CustomScreen.tsx` for a full example of integrating both **Sound** and **Custom Player** logic together.

```tsx
import React, { useState } from "react";
import { View, Text, Button } from "react-native";
import AudioPlayer, {
  useProgress,
} from "@vatsaldarji/react-native-audio-player";

const CustomScreen = () => {
  const playerId = "custom-player";
  const [isPlaying, setIsPlaying] = useState(false);
  const [audioUrl] = useState(
    "https://www.learningcontainer.com/wp-content/uploads/2020/02/Kalimba.mp3"
  );
  const progress = useProgress(playerId);

  const handlePlay = async () => {
    await AudioPlayer.initialize(playerId);
    await AudioPlayer.load(playerId, audioUrl);
    await AudioPlayer.play(playerId);
    setIsPlaying(true);
  };

  const handlePause = async () => {
    await AudioPlayer.pause(playerId);
    setIsPlaying(false);
  };

  return (
    <View>
      <Text>🎵 Audio Player Demo</Text>

      <Button
        title={isPlaying ? "Pause" : "Play"}
        onPress={isPlaying ? handlePause : handlePlay}
      />

      <Text>Position: {Math.floor(progress.position / 1000)}s</Text>
    </View>
  );
};

export default CustomScreen;
```

---

## 🧑‍💻 Author

**[@vatsaldarji](https://www.npmjs.com/~vatsaldarji)**  
React Native Developer • Audio Systems Enthusiast

---

## 🪄 Coming Soon

- Real-time waveform rendering
- Audio visualization toolkit
- iOS support

---

## 📄 License

MIT © 2025 [Vatsal Darji](https://github.com/vatsaldarji)
