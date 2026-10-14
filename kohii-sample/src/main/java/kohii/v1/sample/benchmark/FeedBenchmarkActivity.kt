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
  private val pending = mutableSetOf<Any>()

  override fun onBuffering(
    playback: Playback,
    playWhenReady: Boolean,
  ) {
    if (playWhenReady && pending.add(playback.tag) && Build.VERSION.SDK_INT >= 29) {
      Trace.beginAsyncSection(FeedBenchmarkActivity.FIRST_FRAME_SECTION, playback.tag.hashCode())
    }
  }

  override fun onRendered(playback: Playback) {
    if (pending.remove(playback.tag) && Build.VERSION.SDK_INT >= 29) {
      Trace.endAsyncSection(FeedBenchmarkActivity.FIRST_FRAME_SECTION, playback.tag.hashCode())
    }
  }
}
