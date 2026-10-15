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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle.State
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Regression tests for promoting and demoting sticky Buckets with [Manager.stick] and
 * [Manager.unstick].
 */
@RunWith(AndroidJUnit4::class)
class StickyBucketTest {
  private lateinit var activity: FragmentActivity
  private lateinit var manager: Manager
  private lateinit var rootA: FrameLayout
  private lateinit var rootB: FrameLayout

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    // Master is a process-wide singleton bound to the first Application it sees. Each test gets a
    // fresh instance so state does not leak between tests.
    val master =
      Master::class.java.getDeclaredConstructor(Context::class.java)
        .apply { isAccessible = true }
        .newInstance(activity)

    val root = LinearLayout(activity)
    rootA = FrameLayout(activity).also(root::addView)
    rootB = FrameLayout(activity).also(root::addView)
    activity.setContentView(root)

    manager =
      master.registerInternal(
        activity = activity,
        host = activity,
        managerLifecycleOwner = activity,
        activeLifecycleState = State.STARTED,
      )
    manager.addBucket(rootA, rootB)
  }

  @Test
  fun `sticking another Bucket demotes the previous sticky Bucket`() {
    val (bucketA, bucketB) = manager.buckets.toList()

    manager.stick(bucketA)
    manager.stick(bucketB)
    manager.unstick(bucketB)

    assertEquals(listOf(bucketA, bucketB), manager.buckets.toList())
  }

  @Test
  fun `removing the sticky Bucket removes every copy of it`() {
    val bucketA = manager.buckets.first()

    manager.stick(bucketA)
    manager.removeBucket(rootA)

    assertTrue(manager.buckets.none { it.root === rootA })

    manager.addBucket(rootA)
    val readded = manager.buckets.single { it.root === rootA }
    assertNotSame(bucketA, readded)
  }
}
