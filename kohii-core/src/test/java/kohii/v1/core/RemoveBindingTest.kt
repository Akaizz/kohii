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

package kohii.v1.core

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import kohii.v1.internal.StaticViewRendererPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Regression tests for removing Playbacks through [Manager.removeBucket] and [Engine.cancel]
 * (which delegates to [Master.removeBinding]).
 */
@RunWith(AndroidJUnit4::class)
class RemoveBindingTest {
  private lateinit var activity: FragmentActivity
  private lateinit var master: Master

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    // Master is a process-wide singleton bound to the first Application it sees. Each test gets a
    // fresh instance so state does not leak between tests.
    master =
      Master::class.java.getDeclaredConstructor(Context::class.java)
        .apply { isAccessible = true }
        .newInstance(activity)
  }

  @Test
  fun `removeBucket removes the Playbacks of that Bucket`() {
    val root = LinearLayout(activity)
    val container = FrameLayout(activity).also(root::addView)
    activity.setContentView(root)

    val manager = registerManager(activity, activity)
    manager.addBucket(root)
    val playback = addPlayback(manager, container)
    assertSame(playback, manager.playbacks[container])

    manager.removeBucket(root)

    assertNull(manager.playbacks[container])
    assertTrue(manager.buckets.isEmpty())
  }

  @Test
  fun `removeBinding removes a Playback owned by a Manager other than the first one`() {
    val activityRoot = LinearLayout(activity)
    val fragmentRoot = LinearLayout(activity)
    val container = FrameLayout(activity).also(fragmentRoot::addView)
    activityRoot.addView(fragmentRoot)
    activity.setContentView(activityRoot)

    val fragment = Fragment()
    activity.supportFragmentManager.beginTransaction()
      .add(fragment, "fragment")
      .commitNow()

    val activityManager = registerManager(activity, activity)
    val fragmentManager = registerManager(fragment, fragment)
    // The container belongs to the second Manager of the Group.
    assertEquals(listOf(activityManager, fragmentManager), activityManager.group.managers.toList())

    fragmentManager.addBucket(fragmentRoot)
    val playback = addPlayback(fragmentManager, container)
    assertSame(playback, fragmentManager.playbacks[container])

    master.removeBinding(container)

    assertNull(fragmentManager.playbacks[container])
  }

  private fun registerManager(
    host: Any,
    lifecycleOwner: LifecycleOwner,
  ): Manager =
    master.registerInternal(
      activity = activity,
      host = host,
      managerLifecycleOwner = lifecycleOwner,
      activeLifecycleState = State.STARTED,
    )

  private fun addPlayback(
    manager: Manager,
    container: FrameLayout,
  ): Playback {
    val bucket = manager.buckets.single()
    return StaticViewRendererPlayback(manager, bucket, container, Playback.Config())
      .also(manager::addPlayback)
  }
}
