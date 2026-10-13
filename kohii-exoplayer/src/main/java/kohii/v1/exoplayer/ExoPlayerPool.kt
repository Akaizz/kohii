/*
 * Copyright (c) 2020 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(UnstableApi::class)

package kohii.v1.exoplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import kohii.v1.core.Common.getUserAgent
import kohii.v1.core.PlayerPool
import kohii.v1.media.Media

/**
 * A [PlayerPool] for the [Player] implementation. By default it uses the [KohiiExoPlayer]
 * implementation.
 *
 * @see [KohiiExoPlayer]
 */
class ExoPlayerPool(
  poolSize: Int = DEFAULT_POOL_SIZE,
  private val context: Context,
  private val userAgent: String = getUserAgent(context.applicationContext, BuildConfig.LIB_NAME),
  private val clock: Clock = Clock.DEFAULT,
  private val bandwidthMeterFactory: BandwidthMeterFactory = ExoPlayerConfig.DEFAULT,
  private val trackSelectorFactory: TrackSelectorFactory = ExoPlayerConfig.DEFAULT,
  private val loadControlFactory: LoadControlFactory = ExoPlayerConfig.DEFAULT,
  private val renderersFactory: RenderersFactory =
    DefaultRenderersFactory(context.applicationContext),
  private val cache: Cache? = null,
  // DefaultMediaSourceFactory
  // @RestrictTo(LIBRARY_GROUP)
  // Note: Only used within the library group. Client must not access this field.
  val defaultMediaSourceFactory: DefaultMediaSourceFactory =
    with(context) {
      val httpDataSource = DefaultHttpDataSource.Factory().setUserAgent(userAgent)

      // DefaultMediaSourceFactory
      val mediaCache: Cache = cache ?: ExoPlayerCache.lruCacheSingleton.get(context)
      val upstreamFactory = DefaultDataSource.Factory(context, httpDataSource)
      val drmSessionManagerProvider = DefaultDrmSessionManagerProvider()
      drmSessionManagerProvider.setDrmHttpDataSourceFactory(httpDataSource)

      DefaultMediaSourceFactory(
        // dataSourceFactory
        CacheDataSource.Factory()
          .setCache(mediaCache)
          .setUpstreamDataSourceFactory(upstreamFactory)
          .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
      )
        .setDrmSessionManagerProvider(drmSessionManagerProvider)
        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy())
    },
) : PlayerPool<Player>(poolSize) {
  override fun createPlayer(media: Media): Player =
    KohiiExoPlayer(
      context = context.applicationContext,
      clock = clock,
      renderersFactory = renderersFactory,
      trackSelector = trackSelectorFactory.createDefaultTrackSelector(context.applicationContext),
      loadControl = loadControlFactory.createLoadControl(),
      bandwidthMeter = bandwidthMeterFactory.createBandwidthMeter(context.applicationContext),
      mediaSourceFactory = defaultMediaSourceFactory,
      looper = Util.getCurrentOrMainLooper(),
    )

  override fun resetPlayer(player: Player) {
    super.resetPlayer(player)
    player.stop()
    player.clearMediaItems()
    if (player is ExoPlayer) {
      player.setAudioAttributes(AudioAttributes.DEFAULT, true)
    }
  }

  override fun destroyPlayer(player: Player) = player.release()
}
