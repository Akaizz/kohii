# Getting start

**Kohii** is an Android library, therefore to use it, you need to add its artifacts to your
module's `build.gradle` dependency list. **Kohii** is released
the `sonatype maven central repository`, so before adding **Kohii** to your project, make sure the
following lines exist in your project root `build.gradle` file:

```groovy
allprojects {
  repositories {
    mavenCentral()
  }
}
```

**Kohii** has the following artifacts:

- `kohii-core`

This artifact contains core design of the library. It doesn't contain enough implementation detail
to start the playback. To do so you need to include one of the following artifacts:

- `kohii-exoplayer`

This artifact contains the implementation of `kohii-core` that uses **ExoPlayer** as playback *
backend*. As a result, you will also need to include necessary **ExoPlayer** artifacts as well. Your
dependency to use `kohii-exoplayer` will look like below:

```groovy
def kohiiVersion = '1.4.0.2017001' // always use latest available version
def exoplayerVersion = '2.17.1'

implementation "im.ene.kohii:kohii-core:${kohiiVersion}"
implementation "im.ene.kohii:kohii-exoplayer:${kohiiVersion}"
implementation "com.google.android.exoplayer:exoplayer:${exoplayerVersion}"
```

!!! info "Regarding Kohii version name structure"
**Kohii** extension for **ExoPlayer** uses a specific version of **ExoPlayer** for the playback
logic. Because **ExoPlayer** doesn't guarantee backward compatibility on each minor release, **
Kohii** includes the **ExoPlayer** release number in its release name to tell developer about which
version of **ExoPlayer** it uses. For example **Kohii** `1.0.0.2010004` will use **
ExoPlayer** `2.10.4` for playback logic, but **Kohii** `1.0.0.2011000` will use **
ExoPlayer** `2.11.0` instead.
