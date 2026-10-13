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
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kohii.v1.exoplayer.ExoPlayerCache.downloadCacheSingleton
import kohii.v1.exoplayer.ExoPlayerCache.lruCacheSingleton
import kohii.v1.logWarn
import kohii.v1.utils.Capsule
import java.io.File

/**
 * A convenient object to help creating and reusing a [Cache] for the media content. It supports
 * a [lruCacheSingleton] which is a [SimpleCache] that uses the [LeastRecentlyUsedCacheEvictor]
 * internally, and a [downloadCacheSingleton] which is a [SimpleCache] that doesn't evict cache,
 * which is useful to store downloaded content.
 */
object ExoPlayerCache {
  private const val CACHE_CONTENT_DIRECTORY = "kohii_content"
  private const val DOWNLOAD_CONTENT_DIRECTORY = "kohii_content_download"
  private const val MIN_CACHE_SIZE = 32 * 1024 * 1024L // 32 Megabytes
  private const val MAX_CACHE_SIZE = 256 * 1024 * 1024L // 256 Megabytes

  // The cache size is 10% of the free space, between MIN_CACHE_SIZE and MAX_CACHE_SIZE, so a feed
  // of many videos can keep the recently watched ones without filling up small devices.
  private fun cacheSizeFor(directory: File): Long =
    (directory.usableSpace / 10).coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE)

  private val lruCacheCreator: (Context) -> Cache = { context ->
    val databaseProvider = StandaloneDatabaseProvider(context)
    deleteLegacyLruCache(context, databaseProvider)
    // Stored in the cache directory, so the system can reclaim the space when the device is low on
    // storage. SimpleCache handles the files removed by the system.
    val directory = File(context.cacheDir, CACHE_CONTENT_DIRECTORY)
    SimpleCache(
      directory,
      LeastRecentlyUsedCacheEvictor(cacheSizeFor(context.cacheDir)),
      databaseProvider,
    )
  }

  // Older versions stored the LRU cache in the external files directory. Delete it in background, as
  // it is not used anymore.
  private fun deleteLegacyLruCache(
    context: Context,
    databaseProvider: StandaloneDatabaseProvider,
  ) {
    val legacyDirectory =
      File(context.getExternalFilesDir(null) ?: context.filesDir, CACHE_CONTENT_DIRECTORY)
    if (!legacyDirectory.exists()) return
    Thread {
      try {
        SimpleCache.delete(legacyDirectory, databaseProvider)
      } catch (error: Exception) {
        logWarn { "Failed to delete the legacy cache at $legacyDirectory: $error" }
      }
    }.start()
  }

  private val downloadCacheCreator: (Context) -> Cache = { context ->
    SimpleCache(
      File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        DOWNLOAD_CONTENT_DIRECTORY,
      ),
      NoOpCacheEvictor(),
      StandaloneDatabaseProvider(context),
    )
  }

  /**
   * A reusable [Cache] that uses the [LeastRecentlyUsedCacheEvictor] internally. It is stored in the
   * app's cache directory and uses 10% of the free space, between 32 MB and 256 MB.
   *
   * Usage:
   *
   * ```kotlin
   * val cache = ExoPlayerCache.lruCacheSingleton.get(context)
   * ```
   */
  val lruCacheSingleton = Capsule(lruCacheCreator)

  /**
   * A reusable [Cache] that uses the [NoOpCacheEvictor] internally.
   *
   * Usage:
   *
   * ```kotlin
   * val cache = ExoPlayerCache.downloadCacheSingleton.get(context)
   * ```
   */
  val downloadCacheSingleton = Capsule(downloadCacheCreator)
}
