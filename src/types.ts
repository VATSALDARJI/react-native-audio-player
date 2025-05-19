import { NativeEventEmitter } from "react-native";
import { Events } from "./constants";

export interface PlaybackStateEvent {
  playerId: string;
  duration: number;
  currentPosition: number;
  bufferedPosition: number;
  isPlaying: boolean;
}

export interface PlaybackFinishedEvent {
  playerId: string;
}

export interface PlaybackLoadedEvent {
  playerId: string;
}

export interface PlaybackBufferingEvent {
  playerId: string;
  isBuffering: boolean;
}

export interface BufferedProgressEvent {
  playerId: string;
  bufferedPosition: number;
  bufferedPercentage: number;
}

export interface PositionChangedEvent {
  playerId: string;
  currentPosition: number;
  duration: number;
  playbackSpeed: number;
}

// Event mapping to type interfaces
export interface EventTypeMapping {
  [Events.ON_PLAYBACK_FINISHED]: PlaybackFinishedEvent;
  [Events.ON_PLAYBACK_STATE_CHANGED]: PlaybackStateEvent;
  [Events.ON_LOADED]: PlaybackLoadedEvent;
  [Events.ON_BUFFERING]: PlaybackBufferingEvent;
  [Events.ON_BUFFERED_PROGRESS]: BufferedProgressEvent;
  [Events.ON_POSITION_CHANGED]: PositionChangedEvent;
}

export interface AudioPlayerInterface {
  initialize(playerId: string): Promise<void>;
  hasPlayer(playerId: string): Promise<boolean>;
  load(playerId: string, url: string): Promise<void>;
  play(playerId: string): Promise<void>;
  pause(playerId: string): Promise<void>;
  stop(playerId: string): Promise<void>;
  seekTo(playerId: string, position: number): Promise<void>;
  getDuration(playerId: string): Promise<number>;
  getCurrentPosition(playerId: string): Promise<number>;
  getCurrentLiveOffset(playerId: string): Promise<number>;
  setVolume(playerId: string, volume: number): Promise<void>;
  setRate(playerId: string, rate: number): Promise<void>;
  setPitch(playerId: string, pitch: number): Promise<void>;
  setPositionUpdateInterval(
    playerId: string,
    intervalMs: number
  ): Promise<void>;
  release(playerId: string): Promise<void>;
  getWaveformData(): Promise<number[]>;
  getBufferedPosition(): Promise<number>;
  addPositionListener(callback: (position: number) => void): {
    remove: () => void;
  };
  addPlayStateListener(callback: (isPlaying: boolean) => void): {
    remove: () => void;
  };
  addBufferingListener(
    playerId: string,
    callback: (bufferedPosition: number) => void
  ): {
    remove: () => void;
  };
  addEventListener<T extends keyof EventTypeMapping>(
    event: T,
    callback: (event: EventTypeMapping[T]) => void
  ): {
    remove: () => void;
  };
  eventEmitter: NativeEventEmitter;
}
