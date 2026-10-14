package kohii.v1.sample.benchmark

import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kohii.v1.core.Playback
import kohii.v1.exoplayer.Kohii
import kohii.v1.sample.DemoApp
import kohii.v1.sample.R

/**
 * A vertical video feed used by the macrobenchmarks in `:kohii-benchmark`.
 *
 * The videos are passed as a comma separated list in [EXTRA_URLS] and repeated to fill
 * [ITEM_COUNT] items. Without it, the local video asset is used, so the scroll benchmark does not
 * depend on the network.
 *
 * Each time a video starts, the time until its first frame is rendered is recorded as the
 * [FIRST_FRAME_SECTION] async trace section.
 */
class FeedBenchmarkActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_URLS = "kohii.v1.sample.benchmark.URLS"
    const val FIRST_FRAME_SECTION = "KohiiFirstFrame"
    private const val ITEM_COUNT = 60
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_feed_benchmark)

    val urls =
      intent.getStringExtra(EXTRA_URLS)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
        .ifEmpty { listOf(DemoApp.VIDEO_URI_ASSET) }

    val feed: RecyclerView = findViewById(R.id.benchmarkFeed)
    feed.setHasFixedSize(true)
    feed.layoutManager = LinearLayoutManager(this)

    val kohii = Kohii[this]
    kohii.register(this).addBucket(feed)
    feed.adapter = FeedAdapter(kohii, List(ITEM_COUNT) { urls[it % urls.size] })
  }
}

private class FeedAdapter(
  private val kohii: Kohii,
  private val urls: List<String>,
) : RecyclerView.Adapter<FeedViewHolder>() {
  private val firstFrameTracer = FirstFrameTracer()

  override fun getItemCount(): Int = urls.size

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ): FeedViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.holder_feed_benchmark_video, parent, false)
    return FeedViewHolder(view as PlayerView)
  }

  override fun onBindViewHolder(
    holder: FeedViewHolder,
    position: Int,
  ) {
    val binder =
      kohii.setUp(urls[position]) {
        tag = "benchmark:$position"
        repeatMode = Player.REPEAT_MODE_ONE
      }
    binder.bind(holder.playerView) { playback ->
      // The callback can run again for the same Playback: avoid adding the listener twice.
      playback.removeStateListener(firstFrameTracer)
      playback.addStateListener(firstFrameTracer)
    }
  }
}

private class FeedViewHolder(val playerView: PlayerView) : RecyclerView.ViewHolder(playerView)

/**
 * Records the time from the moment a Playback is asked to play (buffering with playWhenReady) to
 * its first rendered frame, as an async trace section that macrobenchmarks can read.
 */
private class FirstFrameTracer : Playback.StateListener {
  // Cookie of the pending section of each Playback, by tag. Each attempt has its own cookie, so that
  // an attempt paused before its first frame does not end in a later attempt of the same Playback.
  private val pending = mutableMapOf<Any, Int>()
  private var nextCookie = 0

  override fun onBuffering(
    playback: Playback,
    playWhenReady: Boolean,
  ) {
    if (Build.VERSION.SDK_INT < 29) return
    if (playWhenReady) {
      if (playback.tag in pending) return
      val cookie = nextCookie++
      pending[playback.tag] = cookie
      Trace.beginAsyncSection(FeedBenchmarkActivity.FIRST_FRAME_SECTION, cookie)
    } else {
      // Paused before the first frame: leave the section unfinished, it is not counted.
      pending.remove(playback.tag)
    }
  }

  override fun onPaused(playback: Playback) {
    pending.remove(playback.tag)
  }

  override fun onRendered(playback: Playback) {
    val cookie = pending.remove(playback.tag) ?: return
    if (Build.VERSION.SDK_INT >= 29) {
      Trace.endAsyncSection(FeedBenchmarkActivity.FIRST_FRAME_SECTION, cookie)
    }
  }
}
