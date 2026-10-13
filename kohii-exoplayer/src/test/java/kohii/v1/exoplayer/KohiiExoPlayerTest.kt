/*
 * Copyright (c) 2026 Nam Nguyen, nam@ene.im
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

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class KohiiExoPlayerTest {
  @Test
  fun `getAudioSessionId returns the session id of the wrapped player`() {
    val wrapped = mock<ExoPlayer> { on { audioSessionId } doReturn 42 }

    assertEquals(42, KohiiExoPlayer(wrapped).audioSessionId)
  }
}
