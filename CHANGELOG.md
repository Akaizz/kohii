# Change Log

## [Unreleased]

### Changed

- Migrated from ExoPlayer 2 (`com.google.android.exoplayer2`) to AndroidX Media3 1.4.1
  (`androidx.media3`). Compile SDK is 34 (required by Media3), target SDK is still 33.
- (kohii-core): `media3-common` is now an `api` dependency, so `Player`, `C`, ... are available to
  clients without extra dependencies.
- (kohii-core): `PlayerEventListeners` and `Playback` no longer use deprecated `Player.Listener`
  callbacks (`onPlayerStateChanged`, `onCues(List)`). `PlayerEventListeners` also forwards
  `onEvents`, `onIsPlayingChanged`, `onMediaItemTransition`, `onPlayerErrorChanged` and
  `onPlaybackSuppressionReasonChanged`.
- (kohii-exoplayer): `media3-exoplayer` and `media3-ui` are now `api` dependencies. DASH, HLS and
  SmoothStreaming modules are included like the former `exoplayer` bundle.
- (kohii-exoplayer): Media3 merged `StyledPlayerView` into `androidx.media3.ui.PlayerView`, so
  `Kohii`, `createKohii`, `PlayerViewBridge`, `PlayerViewPlayable`, `PlayerViewPlayableCreator`,
  `PlayerViewBridgeCreator` and `PlayerViewProvider` are the recommended (non-deprecated) API again.
- (kohii-exoplayer): `StyledPlayerViewEngine`, `StyledPlayerViewBridge`, ... are now deprecated
  type aliases of the `PlayerView` counterparts, and `createStyledPlayerViewEngine` is deprecated in
  favor of `createKohii`. Use the IDE quick-fix (ReplaceWith) to migrate.
- (kohii-exoplayer): `ExoPlayerConfig.trackSelectorParameters` is nullable; `null` (default) uses
  the default `DefaultTrackSelector.Parameters` for the Application Context.
- (kohii-exoplayer): `ExoPlayerConfig.DEFAULT` buffers up to 12 seconds ahead (`minBufferMs` and
  `maxBufferMs`) instead of ExoPlayer's 50 seconds, as videos in a feed are often skipped after a
  few seconds.
- (kohii-exoplayer): `ExoPlayerCache.lruCacheSingleton` is stored in the app's cache directory
  (instead of the external files directory) and uses 10% of the free space, between 32 MB and
  256 MB (instead of 24 MB). The cache in the old location is deleted.
- (kohii-exoplayer): The library opts in to Media3 `@UnstableApi` internally, so clients only need
  to opt in when they use unstable Media3 APIs directly.

### Fixed

- (kohii-exoplayer): `PlayerParameters` (max video size and bitrates) are now applied when the
  Bridge acquires a new player from the pool. Before, they were silently ignored until the next
  change.
- (kohii-exoplayer): All players created from the same `ExoPlayerConfig` now share one
  `BandwidthMeter` (the app-wide `DefaultBandwidthMeter` singleton when the bandwidth settings are
  the defaults), so HLS/DASH streams start at the right quality instead of re-estimating the
  bandwidth for every player.
- (kohii-core): Debug log messages are only built when logging is enabled. The internal log helpers
  now take a lambda (`logDebug { "..." }`) instead of being `String` extensions.

### Migration

- Replace `com.google.android.exoplayer2.*` imports with their `androidx.media3.*` counterparts
  (e.g. `androidx.media3.common.Player`, `androidx.media3.ui.PlayerView`), and
  `com.google.android.exoplayer2.ui.StyledPlayerView` with `androidx.media3.ui.PlayerView`.
- Replace `<com.google.android.exoplayer2.ui.(Styled)PlayerView>` in layouts with
  `<androidx.media3.ui.PlayerView>`.

### Removed

- (kohii-experiments): The module is removed, together with the YouTube demo of the sample app and
  its Google API dependencies. It played YouTube videos through the unofficial
  android-youtube-player library (a WebView).
- (kohii-ads): The module is removed (it was already excluded from the build), together with the
  placeholder Ads screen of the sample app.
- (kohii-androidx): The module is removed. It was built on AndroidX Media2, which is deprecated in
  favor of Media3. Use `kohii-exoplayer` (Media3 ExoPlayer) instead.
- (kohii-experiments): YouTube experiment that uses the (deprecated) Official YouTube Player API.
- (kohii-sample): Demo using the Official YouTube Player API.
- (kohii-exoplayer): DefaultTrackSelectorHolder is removed.

## [1.4.0.2017001]

### Changed

- (kohii-core): Compatible with ExoPlayer 2.17.1.
- (kohii-core): Target SDK and Compile SDK are updated to 32.
- (kohii-core): Kotlin 1.6.21.
- (kohii-exoplayer): ExoPlayer is updated to 2.17.1.

### Removed

- (kohii-core): jcenter repository and its usage.

## [1.3.0.x]

This version is skipped.

## [1.2.0.2011008]

### Added

- (kohii-core): Add `PlayerPool` implementation.
- (kohii-core): Expose `Playback.lock` flag.
- (kohii-exoplayer): Add `ExoPlayerPool` that extends `PlayerPool` and replace
  the `ExoPlayerProvider`.
- (kohii-androidx): Add `MediaPlayerPool` that extends `PlayerPool` and replace
  the `MediaPlayerProvider`.
- (kohii-ads): Initial implementation using ExoPlayer and Ima Extension API.

### Changed

- (kohii-core): Update selection logic. If there is a Playback started manually, the Selector will
  receive that Playback, otherwise it receives the default candidates.
- (kohii-exoplayer): ExoPlayer is updated to 2.11.8.
- (kohii-exoplayer): Use default CacheDataSourceFactory, which uses default fragment size of 5MB.
- (kohii-exoplayer): DefaultMediaSourceFactoryProvider now accepts nullable
  DrmSessionManagerProvider.
- (kohii-exoplayer): Default ExoPlayer implementation will always update the AudioAttributes when
  the Volume setting is called.
- (kohii-core): Always use LOW memory mode if system needs it. (#127)
- (kohii-core): Using ProcessLifecycleOwner instead of screen state broadcast for system-wide toggle
  the playback. (#125)

### Removed

- (kohii-exoplayer): Remove `ExoPlayerProvider` and its default implementation.
- (kohii-androidx): Remove `MediaPlayerProvider` and its default implementation.

## [1.1.1.2011003]

### Added

- Fix an issue that cause cache doesn't write to file (https://github.com/eneim/kohii/pull/91);

## [1.1.0.2011003]

- ExoPlayer: 2.11.3
- Support _multiple playback_. Check out [docs](https://eneim.github.io/kohii/usage/advance/)
  and [recipe](https://www.notion.so/00ea153b5378454dbc7a104733cd01d9) for more information.
- Experimental manual playback. Read
  more [here](https://www.notion.so/Manual-playback-controller-in-Kohii-212927dd75de4971bc28d27e4b34911a)
  .
- Add method `Kohii.createControlDispatcher(Playback)`. Read
  more [here](https://www.notion.so/Manual-playback-controller-in-Kohii-212927dd75de4971bc28d27e4b34911a#999cb7749ed74cf9aa1c2329c093401f)
  .
- Introduce `PlayableObserver`, Read
  more [here](https://www.notion.so/8ebea74d5e3347c580209652f374247c).
- Introduce `activeLifecycleState` setting for the Manager. Default to `State.STARTED`. All the
  playbacks in a Manager can be played only if the lifecycle state of the Manager is at least this
  value.
- Experimental `PlayerParameters`, `NetworkTypeChangeListener`: the callback from
  a `NetworkTypeChangeListener` needs to return a `PlayerParameters` value so that the player can
  switch the video/audio quality, resolution, etc.
- Deprecate the `VideoSize`. Its value is no longer used anymore in the library.
- Experimental `initialPlaybackInfo` setting for the Binder. Setting this value will allow the
  Playback to start from a specific `PlaybackInfo` value.
- Experimental `releaseOnInActive` setting for the Binder. Setting this to false will let the
  Playback keep its state when it is inactive, but not yet detached from the Manager.
- Add method `Playback.Controller.setupRenderer(Playback, Any?)`
  and `Playback.Controller.teardownRenderer(Playback, Any?)`. Client can configure the renderer with
  custom manual control logic.
- Support Playback locking: if a Playback is locked, it will still be selected but will not be
  played.
- Add `ExoPlayerCache` to the `kohii-exoplayer` package. It can be used to obtain a pre-built Cache
  easily.
- Add `TrackSelectorFactory`, `LoadControlFactory` to the `kohii-exoplayer` package.
- Add `ExoPlayerConfig` to gather most of the detailed setting for a `SimpleExoPlayer` instance.
- Add `createKohii` convenient methods to easily create `Kohii` instance with custom parameters.
- Add `Engine.lock*` and `Engine.unlock*` methods to support manual lock/unlock an
  Activity/Manager/Bucket or Playback.
- Add a simple demonstration that builds TikTok-alike
  UI/UX: [source code](https://github.com/eneim/kohii/tree/dev-v1/kohii-sample-tiktok).
- Another document hub for
  Kohii: https://www.notion.so/The-Kohii-Library-c89a75e2df2b485391b425b5dc83adce

- **[Breaking]** Rename `Playable#considerRequestRenderer` -> `Playable#setupRenderer`.
- **[Breaking]** Rename `Playable#considerReleaseRenderer` -> `Playable#teardownRenderer`.
- **[Breaking]** `RendererProvider#releaseRenderer` now needs to return a boolean.
- **[Breaking]** `Playback#addCallback` and `Playback#removeCallback` are now internal.
- **[Breaking]** The `DefaultControlDispatcher` is now internal.
- **[Breaking]** Include Playback in the `ArtworkHintListener#onArtworkHint`.
- **[Breaking]** Remove default implementations for `BandwidthMeterFactory`.

## [1.0.0.2010004]

_2020.03.15_

- Open `Builder` for `Kohii` and `Latte`. Clients can now use custom implementations to construct
  those classes. Example usages:

```Kotlin
val kohii = Kohii.Builder(context)
    .setPlayableCreator(customPlayableCreator)
    .build()
```

## 1.0.0.2010004-beta.5

Experiment release for `Bucket.Selector`, usage is not finalized yet. Currently it is proved to
allow select multiple Playbacks for a Bucket, but finally only one Playback will be picked to play
by the Group.

- Add `@JvmOverloads` annotation to support calling from Java.
- Other internal improvements.

## 1.0.0.2010004-beta.3

_2019.12.18_

> This is the very first release that is documented. Please
> visit [document page](https://eneim.github.io/kohii) for detail.

- First public beta release.
