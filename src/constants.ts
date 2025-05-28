export const Events = {
  ON_PLAYBACK_FINISHED: 'onPlaybackFinished',
  ON_PLAYBACK_STATE_CHANGED: 'onPlaybackStateChanged',
  ON_LOADED: 'onLoaded',
  ON_BUFFERING: 'onBuffering',
  ON_BUFFERED_PROGRESS: 'onBufferedProgress',
  ON_POSITION_CHANGED: 'onPositionChanged',
  ON_WAVEFORM_DATA: 'onWaveformData',
} as const;

export type EventType = (typeof Events)[keyof typeof Events];
