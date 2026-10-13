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

package kohii.v1.sample.ui.manual

import android.view.ViewGroup
import kohii.v1.core.Playback
import kohii.v1.sample.common.ViewBindingHolder
import kohii.v1.sample.databinding.ManualVideoHolderBinding
import androidx.media3.ui.R as Media3UiR

internal class ManualVideoViewHolder(
  parent: ViewGroup,
) : ViewBindingHolder<ManualVideoHolderBinding>(parent, ManualVideoHolderBinding::inflate),
  Playback.StateListener {
  init {
    showPlaying(false)
  }

  // Media3 merged the play and pause buttons into a single exo_play_pause button.
  fun showPlaying(playing: Boolean) {
    val button = binding.controller.exoPlayPause
    if (playing) {
      button.setImageResource(Media3UiR.drawable.exo_legacy_controls_pause)
      button.contentDescription =
        button.context.getString(Media3UiR.string.exo_controls_pause_description)
    } else {
      button.setImageResource(Media3UiR.drawable.exo_legacy_controls_play)
      button.contentDescription =
        button.context.getString(Media3UiR.string.exo_controls_play_description)
    }
  }

  override fun onPlaying(playback: Playback) = showPlaying(true)

  override fun onPaused(playback: Playback) = showPlaying(false)
}
