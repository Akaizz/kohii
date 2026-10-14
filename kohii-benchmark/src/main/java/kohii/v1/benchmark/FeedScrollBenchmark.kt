package kohii.v1.benchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolls the video feed of kohii-sample (`FeedBenchmarkActivity`) and measures:
 *
 * - [FrameTimingMetric]: frame durations while scrolling and playing (jank).
 * - `KohiiFirstFrame`: time from a video being asked to play to its first rendered frame, as the
 *   average and the number of videos that rendered.
 *
 * The feed uses the local video asset of the sample by default. Pass real feed URLs (MP4, m3u8...)
 * with the `feedUrls` instrumentation argument, see README.md.
 */
@RunWith(AndroidJUnit4::class)
class FeedScrollBenchmark {
  @get:Rule
  val rule = MacrobenchmarkRule()

  @OptIn(ExperimentalMetricApi::class)
  @Test
  fun scrollFeed() =
    rule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics =
        listOf(
          FrameTimingMetric(),
          TraceSectionMetric(FIRST_FRAME_SECTION, TraceSectionMetric.Mode.Average),
          TraceSectionMetric(FIRST_FRAME_SECTION, TraceSectionMetric.Mode.Count),
        ),
      compilationMode = CompilationMode.DEFAULT,
      iterations = ITERATIONS,
      setupBlock = {
        // Start every iteration with an empty video cache, so that later iterations do not load
        // the videos from the cache of the previous ones.
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        pressHome()
        startActivityAndWait(feedIntent())
      },
    ) {
      scrollThroughFeed()
    }

  private fun MacrobenchmarkScope.scrollThroughFeed() {
    val feed = device.wait(Until.findObject(By.res(packageName, "benchmarkFeed")), TIMEOUT_MS)
    checkNotNull(feed) { "The benchmark feed was not found." }
    // Keep the gestures away from the system navigation areas.
    feed.setGestureMargin(device.displayWidth / 5)

    // Let the first video start before scrolling.
    SystemClock.sleep(SETTLE_MS)
    repeat(SCROLL_COUNT) {
      feed.scroll(Direction.DOWN, SCROLL_PERCENT)
      // Give Kohii time to select and start the next video.
      SystemClock.sleep(SETTLE_MS)
    }
  }

  private fun feedIntent(): Intent {
    val intent = Intent(FEED_ACTION).setPackage(TARGET_PACKAGE)
    val urls = InstrumentationRegistry.getArguments().getString(ARG_FEED_URLS)
    if (!urls.isNullOrBlank()) intent.putExtra(EXTRA_URLS, urls)
    return intent
  }

  private companion object {
    const val TARGET_PACKAGE = "kohii.v1.sample"
    const val FEED_ACTION = "kohii.v1.sample.action.FEED_BENCHMARK"

    // Keep in sync with kohii.v1.sample.benchmark.FeedBenchmarkActivity.
    const val EXTRA_URLS = "kohii.v1.sample.benchmark.URLS"
    const val FIRST_FRAME_SECTION = "KohiiFirstFrame"

    const val ARG_FEED_URLS = "feedUrls"
    const val ITERATIONS = 5
    const val SCROLL_COUNT = 10
    const val SCROLL_PERCENT = 0.8F
    const val SETTLE_MS = 1_500L
    const val TIMEOUT_MS = 5_000L
  }
}
