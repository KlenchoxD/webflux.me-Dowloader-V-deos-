package androidx.media3.session;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * Compatibility connector used by the app's legacy MediaSessionCompat flow after migrating
 * playback to Media3.
 */
public final class MediaSessionConnector {
    @NonNull
    private final MediaSessionCompat mediaSession;

    @Nullable
    private Player player;
    @Nullable
    private PlaybackPreparer playbackPreparer;
    @Nullable
    private QueueNavigator queueNavigator;
    @Nullable
    private MediaButtonEventHandler mediaButtonEventHandler;
    @Nullable
    private MediaMetadataProvider mediaMetadataProvider;
    @NonNull
    private CustomActionProvider[] customActionProviders = new CustomActionProvider[0];

    @Nullable
    private CharSequence customErrorMessage;
    private int customErrorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onTimelineChanged(@NonNull final Timeline timeline,
                                      @Player.TimelineChangeReason final int reason) {
            if (queueNavigator != null && player != null) {
                queueNavigator.onTimelineChanged(player);
            }
            updatePlaybackState();
        }

        @Override
        public void onMediaItemTransition(@Nullable final MediaItem mediaItem,
                                          @Player.MediaItemTransitionReason final int reason) {
            if (queueNavigator != null && player != null) {
                queueNavigator.onCurrentMediaItemIndexChanged(player);
            }
            invalidateMediaSessionMetadata();
            updatePlaybackState();
        }

        @Override
        public void onPlaybackStateChanged(@Player.State final int playbackState) {
            updatePlaybackState();
        }

        @Override
        public void onPlayWhenReadyChanged(final boolean playWhenReady,
                                           @Player.PlayWhenReadyChangeReason final int reason) {
            updatePlaybackState();
        }
    };

    public MediaSessionConnector(@NonNull final MediaSessionCompat mediaSession) {
        this.mediaSession = Objects.requireNonNull(mediaSession);
        this.mediaSession.setCallback(new SessionCallback());
        updatePlaybackState();
    }

    public void setPlayer(@Nullable final Player player) {
        if (this.player != null) {
            this.player.removeListener(playerListener);
        }
        this.player = player;
        if (this.player != null) {
            this.player.addListener(playerListener);
            if (queueNavigator != null) {
                queueNavigator.onTimelineChanged(this.player);
            }
        }
        invalidateMediaSessionMetadata();
        updatePlaybackState();
    }

    public void setPlaybackPreparer(@Nullable final PlaybackPreparer playbackPreparer) {
        this.playbackPreparer = playbackPreparer;
        updatePlaybackState();
    }

    public void setQueueNavigator(@Nullable final QueueNavigator queueNavigator) {
        this.queueNavigator = queueNavigator;
        if (this.queueNavigator != null && player != null) {
            this.queueNavigator.onTimelineChanged(player);
        }
        updatePlaybackState();
    }

    public void setMediaButtonEventHandler(
            @Nullable final MediaButtonEventHandler mediaButtonEventHandler) {
        this.mediaButtonEventHandler = mediaButtonEventHandler;
    }

    public void setMetadataDeduplicationEnabled(final boolean metadataDeduplicationEnabled) {
        // No-op for the compatibility connector; metadata refreshes are explicit.
    }

    public void setMediaMetadataProvider(
            @Nullable final MediaMetadataProvider mediaMetadataProvider) {
        this.mediaMetadataProvider = mediaMetadataProvider;
        invalidateMediaSessionMetadata();
    }

    public void invalidateMediaSessionMetadata() {
        if (mediaMetadataProvider != null && player != null) {
            mediaSession.setMetadata(mediaMetadataProvider.getMetadata(player));
        }
    }

    public void setCustomActionProviders(
            @NonNull final CustomActionProvider... customActionProviders) {
        this.customActionProviders = Arrays.copyOf(
                Objects.requireNonNull(customActionProviders),
                customActionProviders.length);
        updatePlaybackState();
    }

    public void setCustomErrorMessage(@Nullable final CharSequence errorMessage) {
        customErrorMessage = errorMessage;
        customErrorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR;
        updatePlaybackState();
    }

    public void setCustomErrorMessage(
            @Nullable final CharSequence errorMessage,
            final int errorCode) {
        customErrorMessage = errorMessage;
        customErrorCode = errorCode;
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO;

        if (playbackPreparer != null) {
            actions |= playbackPreparer.getSupportedPrepareActions();
        }
        if (queueNavigator != null) {
            actions |= queueNavigator.getSupportedQueueNavigatorActions(player);
        }

        int state = PlaybackStateCompat.STATE_NONE;
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        float speed = 1f;
        if (player != null) {
            position = player.getCurrentPosition();
            speed = player.getPlaybackParameters().speed;
            switch (player.getPlaybackState()) {
                case Player.STATE_IDLE:
                    state = PlaybackStateCompat.STATE_NONE;
                    break;
                case Player.STATE_BUFFERING:
                    state = PlaybackStateCompat.STATE_BUFFERING;
                    break;
                case Player.STATE_READY:
                    state = player.getPlayWhenReady()
                            ? PlaybackStateCompat.STATE_PLAYING
                            : PlaybackStateCompat.STATE_PAUSED;
                    break;
                case Player.STATE_ENDED:
                    state = PlaybackStateCompat.STATE_STOPPED;
                    break;
                default:
                    state = PlaybackStateCompat.STATE_NONE;
                    break;
            }
        }

        final PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, speed);

        if (customErrorMessage != null) {
            builder.setErrorMessage(customErrorCode, customErrorMessage);
        }

        if (player != null) {
            for (final CustomActionProvider provider : customActionProviders) {
                final PlaybackStateCompat.CustomAction customAction =
                        provider.getCustomAction(player);
                if (customAction != null) {
                    builder.addCustomAction(customAction);
                }
            }
            if (queueNavigator != null) {
                builder.setActiveQueueItemId(queueNavigator.getActiveQueueItemId(player));
            }
        }

        mediaSession.setPlaybackState(builder.build());
    }

    private final class SessionCallback extends MediaSessionCompat.Callback {
        @Override
        public boolean onMediaButtonEvent(@NonNull final Intent mediaButtonEvent) {
            if (mediaButtonEventHandler != null && mediaButtonEventHandler.onMediaButtonEvent(
                    player, mediaButtonEvent)) {
                return true;
            }
            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onPlay() {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepare(true);
            } else if (player != null) {
                player.play();
            }
        }

        @Override
        public void onPause() {
            if (player != null) {
                player.pause();
            }
        }

        @Override
        public void onStop() {
            if (player != null) {
                player.stop();
            }
        }

        @Override
        public void onSeekTo(final long pos) {
            if (player != null) {
                player.seekTo(pos);
            }
        }

        @Override
        public void onPrepare() {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepare(false);
            }
        }

        @Override
        public void onPrepareFromMediaId(@NonNull final String mediaId,
                                         @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromMediaId(mediaId, false, extras);
            }
        }

        @Override
        public void onPlayFromMediaId(@NonNull final String mediaId,
                                      @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromMediaId(mediaId, true, extras);
            }
        }

        @Override
        public void onPrepareFromSearch(@NonNull final String query,
                                        @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromSearch(query, false, extras);
            }
        }

        @Override
        public void onPlayFromSearch(@NonNull final String query,
                                     @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromSearch(query, true, extras);
            }
        }

        @Override
        public void onPrepareFromUri(@NonNull final Uri uri,
                                     @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromUri(uri, false, extras);
            }
        }

        @Override
        public void onPlayFromUri(@NonNull final Uri uri,
                                  @Nullable final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromUri(uri, true, extras);
            }
        }

        @Override
        public void onSkipToPrevious() {
            if (queueNavigator != null && player != null) {
                queueNavigator.onSkipToPrevious(player);
            }
        }

        @Override
        public void onSkipToNext() {
            if (queueNavigator != null && player != null) {
                queueNavigator.onSkipToNext(player);
            }
        }

        @Override
        public void onSkipToQueueItem(final long id) {
            if (queueNavigator != null && player != null) {
                queueNavigator.onSkipToQueueItem(player, id);
            }
        }

        @Override
        public void onCustomAction(@NonNull final String action, @Nullable final Bundle extras) {
            if (player == null) {
                return;
            }
            for (final CustomActionProvider provider : customActionProviders) {
                final PlaybackStateCompat.CustomAction customAction =
                        provider.getCustomAction(player);
                if (customAction != null && action.equals(customAction.getAction())) {
                    provider.onCustomAction(player, action, extras);
                    return;
                }
            }
        }

        @Override
        public void onCommand(@NonNull final String command,
                              @Nullable final Bundle extras,
                              @Nullable final ResultReceiver cb) {
            if (player == null) {
                return;
            }
            if (playbackPreparer != null
                    && playbackPreparer.onCommand(player, command, extras, cb)) {
                return;
            }
            if (queueNavigator != null && queueNavigator.onCommand(player, command, extras, cb)) {
                return;
            }
            super.onCommand(command, extras, cb);
        }
    }

    public interface PlaybackPreparer {
        long getSupportedPrepareActions();

        void onPrepare(boolean playWhenReady);

        void onPrepareFromMediaId(@NonNull String mediaId,
                                  boolean playWhenReady,
                                  @Nullable Bundle extras);

        void onPrepareFromSearch(@NonNull String query,
                                 boolean playWhenReady,
                                 @Nullable Bundle extras);

        void onPrepareFromUri(@NonNull Uri uri,
                              boolean playWhenReady,
                              @Nullable Bundle extras);

        boolean onCommand(@NonNull Player player,
                          @NonNull String command,
                          @Nullable Bundle extras,
                          @Nullable ResultReceiver cb);
    }

    public interface QueueNavigator {
        long getSupportedQueueNavigatorActions(@Nullable Player player);

        void onTimelineChanged(@NonNull Player player);

        void onCurrentMediaItemIndexChanged(@NonNull Player player);

        long getActiveQueueItemId(@Nullable Player player);

        void onSkipToPrevious(@NonNull Player player);

        void onSkipToQueueItem(@NonNull Player player, long id);

        void onSkipToNext(@NonNull Player player);

        boolean onCommand(@NonNull Player player,
                          @NonNull String command,
                          @Nullable Bundle extras,
                          @Nullable ResultReceiver cb);
    }

    public interface CustomActionProvider {
        void onCustomAction(@NonNull Player player,
                            @NonNull String action,
                            @Nullable Bundle extras);

        @Nullable
        PlaybackStateCompat.CustomAction getCustomAction(@NonNull Player player);
    }

    public interface MediaButtonEventHandler {
        boolean onMediaButtonEvent(@Nullable Player player, @NonNull Intent intent);
    }

    public interface MediaMetadataProvider {
        @NonNull
        MediaMetadataCompat getMetadata(@NonNull Player player);
    }
}
