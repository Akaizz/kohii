# Kohii

## kohii (コーヒー、[[ko̞ːçiː]](https://en.wiktionary.org/wiki/%E3%82%B3%E3%83%BC%E3%83%92%E3%83%BC))

<img src="art/kohii.png?raw=true" alt="Kohii" width="384">

Video playback for Android made easy.

<!-- [![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/im.ene.kohii/kohii-core.svg)](https://oss.sonatype.org/content/repositories/snapshots/im/ene/kohii/kohii-core/) -->
![Maven Central](https://img.shields.io/maven-central/v/im.ene.kohii/kohii-core)
[![](https://yourdonation.rocks/images/badge.svg)](https://github.com/sponsors/eneim)

**Kohii** is a powerful, easy to use and extensible Video playback library. **Kohii** provides
powerful features out of the box, including

1. Easy to start: just one line to start a Video playback.
2. Automatic playback experience on RecyclerView, NestedScrollView, ViewPager2, etc.
3. Configuration change handling works out of the box, including the transition from local playback
   to fullscreen playback and vice versa.
4. Feature-rich sample app that covers either simple or advance use cases.
5. Extension-based structure, including default implementations that support ExoPlayer, AndroidX
   Media2, YouTube Player SDK.

## Demo

|Automatic playback|Playback continuity (Seamless fullscreen)|
| :--- | :--- |
|<img src="./art/kohii_demo_2.gif" width="216" height="468"/>|<img src="./art/kohii_demo_3.gif" width="468" height="468"/>|

## Setup

Add to your module's build.gradle dependencies

This fork is distributed through [JitPack](https://jitpack.io/#Akaizz/kohii).

```groovy
// settings.gradle (or top level build.gradle)
repositories {
  google()
  mavenCentral()
  maven { url "https://jitpack.io" }
}
```

```groovy
// <version> is a git tag, a commit hash, or <branch>-SNAPSHOT (for example media3-SNAPSHOT).
implementation "com.github.Akaizz.kohii:kohii-exoplayer:<version>" // also brings kohii-core
// Media3 ExoPlayer (androidx.media3:media3-exoplayer, media3-ui) is included transitively.
// If your app also declares Media3 modules, use the same version as Kohii (1.10.1).
```

Kohii uses Kotlin language with some Java 8 features so you will need to include the following
options to your module's build.gradle:

```groovy
android {
  compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}
```

## Start a playback

```Kotlin tab=
// Kotlin
// 1. Initialization in Fragment or Activity
val kohii = Kohii[this@Fragment]
kohii.register(this /* Fragment or Activity */).addBucket(this.recyclerView)

// 2. In ViewHolder or Adapter: bind the video to the PlayerView inside a child of the RecyclerView.
// playerView is an androidx.media3.ui.PlayerView.
kohii.setUp(videoUrl).bind(playerView)
```

```Java tab=
// Java
// 1. Initialization in Fragment or Activity
Kohii kohii = Kohii.get(this);
kohii.register(this).addBucket(this.recyclerView);

// 2. In ViewHolder or Adapter: bind the video to the PlayerView inside a child of the RecyclerView.
kohii.setUp(videoUrl).bind(playerView);
```

## Requirements

**Kohii** works on Android 7.0+ (API level 24+) and on Java 8+. It is recommended to use Kohii using
Kotlin.

The core library doesn't come with any actual playback logic. Actual implementation comes with
extension libraries: ``kohii-exoplayer`` uses ``androidx.media3`` (ExoPlayer) for playback.
