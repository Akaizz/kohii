/*
 * Copyright (c) 2023 Nam Nguyen, nam@ene.im
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

package kohii.v1.exoplayer

import android.content.Context
import androidx.media3.common.Player
import kohii.v1.core.RendererProviderFactory

/**
 * Media3 merged the former `StyledPlayerView` into [androidx.media3.ui.PlayerView], so this is now
 * just an alias of [Kohii].
 */
@Deprecated(
  message = "StyledPlayerView no longer exists in Media3. Use Kohii instead.",
  replaceWith =
    ReplaceWith(
      expression = "Kohii",
      imports = ["kohii.v1.exoplayer.Kohii"],
    ),
)
typealias StyledPlayerViewEngine = Kohii

/**
 * Creates a new [Kohii] instance using an [ExoPlayerConfig].
 *
 * @see createKohii
 */
@Deprecated(
  message = "StyledPlayerView no longer exists in Media3. Use createKohii instead.",
  replaceWith =
    ReplaceWith(
      expression = "createKohii(context, config)",
      imports = ["kohii.v1.exoplayer.createKohii"],
    ),
)
fun createStyledPlayerViewEngine(
  context: Context,
  config: ExoPlayerConfig,
): Kohii = createKohii(context, config)

/**
 * Creates a new [Kohii] instance using a custom [playerCreator] and [rendererProviderFactory].
 *
 * @see createKohii
 */
@Deprecated(
  message = "StyledPlayerView no longer exists in Media3. Use createKohii instead.",
  replaceWith =
    ReplaceWith(
      expression = "createKohii(context, playerCreator, rendererProviderFactory)",
      imports = ["kohii.v1.exoplayer.createKohii"],
    ),
)
@JvmOverloads
fun createStyledPlayerViewEngine(
  context: Context,
  playerCreator: ((Context) -> Player)? = null,
  rendererProviderFactory: RendererProviderFactory = { PlayerViewProvider() },
): Kohii = createKohii(context, playerCreator, rendererProviderFactory)
