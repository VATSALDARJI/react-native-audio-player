import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  NativeModules,
  NativeEventEmitter,
  Platform,
  requireNativeComponent,
  UIManager,
  findNodeHandle,
  ViewStyle,
  TouchableOpacity,
  Text,
} from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-audio-player' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

interface WaveformViewProps {
  style?: ViewStyle;
  audioSource: string;
  waveColor?: string;
  progressColor?: string;
  backgroundColor?: string;
  onProgress?: (progress: number) => void;
  onLoad?: (duration: number) => void;
  onError?: (error: string) => void;
}

interface NativeWaveformViewProps extends WaveformViewProps {
  onWaveformProgress: (event: any) => void;
  onWaveformLoad: (event: any) => void;
  onWaveformError: (event: any) => void;
  ref?: React.RefObject<any>;
}

const ComponentName = 'WaveformView';

const WaveformView: React.ComponentType<NativeWaveformViewProps> & { displayName?: string } = 
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<NativeWaveformViewProps>(ComponentName)
    : () => {
        console.error(LINKING_ERROR);
        return null;
      };

WaveformView.displayName = ComponentName;

const { WaveformAudioModule } = NativeModules;

export interface WaveformAudioProps {
  source: string;
  style?: ViewStyle;
  waveColor?: string;
  progressColor?: string;
  backgroundColor?: string;
  autoPlay?: boolean;
  onProgress?: (progress: number) => void;
  onLoad?: (duration: number) => void;
  onError?: (error: string) => void;
}

export const WaveformAudio: React.FC<WaveformAudioProps> = ({
  source,
  style,
  waveColor = '#1a1a1a',
  progressColor = '#007AFF',
  backgroundColor = '#ffffff',
  autoPlay = false,
  onProgress,
  onLoad,
  onError,
}) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const waveformRef = useRef<any>(null);

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(WaveformAudioModule);
    
    const subscriptions = [
      eventEmitter.addListener('onPlaybackStateChanged', (event) => {
        setIsPlaying(event.isPlaying);
      }),
      eventEmitter.addListener('onPlaybackComplete', () => {
        setIsPlaying(false);
        setCurrentTime(0);
      }),
    ];

    return () => {
      subscriptions.forEach((subscription) => subscription.remove());
    };
  }, []);

  const handleWaveformProgress = (event: any) => {
    const progress = event.nativeEvent.progress;
    setCurrentTime(progress * duration);
    onProgress?.(progress);
  };

  const handleWaveformLoad = (event: any) => {
    const audioDuration = event.nativeEvent.duration;
    setDuration(audioDuration);
    onLoad?.(audioDuration);

    if (autoPlay) {
      handlePlayPause();
    }
  };

  const handleWaveformError = (event: any) => {
    onError?.(event.nativeEvent.error);
  };

  const handlePlayPause = async () => {
    try {
      const nodeId = findNodeHandle(waveformRef.current);
      if (nodeId) {
        if (isPlaying) {
          await WaveformAudioModule.pause(nodeId);
        } else {
          await WaveformAudioModule.play(nodeId);
        }
      }
    } catch (error) {
      console.error('Error controlling playback:', error);
    }
  };

  const formatTime = (timeInSeconds: number) => {
    const minutes = Math.floor(timeInSeconds / 60);
    const seconds = Math.floor(timeInSeconds % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <View style={[styles.container, style]}>
      <WaveformView
        ref={waveformRef}
        style={styles.waveform}
        audioSource={source}
        waveColor={waveColor}
        progressColor={progressColor}
        backgroundColor={backgroundColor}
        onWaveformProgress={handleWaveformProgress}
        onWaveformLoad={handleWaveformLoad}
        onWaveformError={handleWaveformError}
      />
      <View style={styles.controls}>
        <TouchableOpacity onPress={handlePlayPause} style={styles.playButton}>
          <Text style={styles.playButtonText}>
            {isPlaying ? '⏸️' : '▶️'}
          </Text>
        </TouchableOpacity>
        <Text style={styles.timeText}>
          {formatTime(currentTime)} / {formatTime(duration)}
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    minHeight: 100,
  },
  waveform: {
    flex: 1,
    minHeight: 60,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  playButton: {
    padding: 8,
  },
  playButtonText: {
    fontSize: 24,
  },
  timeText: {
    fontSize: 12,
    color: '#666666',
  },
});

export default WaveformAudio;
