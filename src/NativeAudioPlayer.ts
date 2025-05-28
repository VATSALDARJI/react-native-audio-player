import {
  NativeEventEmitter,
  NativeModules,
  TurboModuleRegistry,
} from 'react-native';
import type { TurboModule } from 'react-native';
import type {
  AudioPlayerInterface,
  EventTypeMapping,
  WaveformDataEvent,
} from './types';
import { Events } from './constants';

interface AudioPlayerSpec extends TurboModule, AudioPlayerInterface {}

const { AudioPlayerModule } = NativeModules;

/** replace all methods using turbo module registry instead of traditional NativeModules package from react native */
const NativeModule =
  TurboModuleRegistry.getEnforcing<AudioPlayerSpec>('AudioPlayerModule');

// Create the event emitter
export const eventEmitter = new NativeEventEmitter(AudioPlayerModule);

// Create a wrapper object that explicitly implements every method in the interface
const AudioPlayer: AudioPlayerSpec = {
  ...NativeModule,
  initialize: (playerId: string): Promise<void> => {
    return NativeModule.initialize(playerId);
  },

  hasPlayer: (playerId: string): Promise<boolean> => {
    return NativeModule.hasPlayer(playerId);
  },

  load: (playerId: string, url: string): Promise<void> => {
    return NativeModule.load(playerId, url);
  },

  play: (playerId: string): Promise<void> => {
    return NativeModule.play(playerId);
  },

  pause: (playerId: string): Promise<void> => {
    return NativeModule.pause(playerId);
  },

  stop: (playerId: string): Promise<void> => {
    return NativeModule.stop(playerId);
  },

  seekTo: (playerId: string, position: number): Promise<void> => {
    return NativeModule.seekTo(playerId, position);
  },

  getDuration: (playerId: string): Promise<number> => {
    return NativeModule.getDuration(playerId);
  },

  getCurrentPosition: (playerId: string): Promise<number> => {
    return NativeModule.getCurrentPosition(playerId);
  },

  getCurrentLiveOffset: (playerId: string): Promise<number> => {
    return NativeModule.getCurrentLiveOffset(playerId);
  },

  setVolume: (playerId: string, volume: number): Promise<void> => {
    return NativeModule.setVolume(playerId, volume);
  },

  setRate: (playerId: string, rate: number): Promise<void> => {
    return NativeModule.setRate(playerId, rate);
  },

  setPitch: (playerId: string, pitch: number): Promise<void> => {
    return NativeModule.setPitch(playerId, pitch);
  },

  setPositionUpdateInterval: (
    playerId: string,
    intervalMs: number
  ): Promise<void> => {
    return NativeModule.setPositionUpdateInterval(playerId, intervalMs);
  },

  release: (playerId: string): Promise<void> => {
    return NativeModule.release(playerId);
  },

  getWaveformData: (playerId: string): Promise<number[]> => {
    return NativeModule.getWaveformData(playerId);
  },

  getBufferedPosition: (): Promise<number> => {
    return NativeModule.getBufferedPosition();
  },

  addPositionListener: (callback: (position: number) => void) => {
    const subscription = eventEmitter.addListener('positionUpdate', callback);
    return {
      remove: () => subscription.remove(),
    };
  },

  addPlayStateListener: (callback: (isPlaying: boolean) => void) => {
    const subscription = eventEmitter.addListener(
      Events.ON_PLAYBACK_STATE_CHANGED,
      (event) => {
        callback(event.isPlaying);
      }
    );
    return {
      remove: () => subscription.remove(),
    };
  },

  addBufferingListener: (
    playerId: string,
    callback: (bufferedPosition: number) => void
  ) => {
    const subscription = eventEmitter.addListener(
      Events.ON_BUFFERED_PROGRESS,
      (event) => {
        if (event.playerId === playerId) {
          callback(event.bufferedPosition);
        }
      }
    );
    return {
      remove: () => subscription.remove(),
    };
  },

  addEventListener: <T extends keyof EventTypeMapping>(
    event: T,
    callback: (event: EventTypeMapping[T]) => void
  ) => {
    const subscription = eventEmitter.addListener(event, callback);
    return {
      remove: () => subscription.remove(),
    };
  },

  addWaveformListener: (
    playerId: string,
    callback: (data: WaveformDataEvent) => void
  ) => {
    const subscription = eventEmitter.addListener(
      Events.ON_WAVEFORM_DATA,
      (event) => {
        if (event.playerId === playerId) {
          callback(event);
        }
      }
    );
    return {
      remove: () => subscription.remove(),
    };
  },

  eventEmitter,
};

export default AudioPlayer;
