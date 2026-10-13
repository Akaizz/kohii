# Review: tech debt và hiệu năng

Phạm vi: `kohii-core`, `kohii-exoplayer`, `kohii-experiments`, `kohii-ads`, cấu hình build và CI,
trên nhánh `media3` (sau khi chuyển sang Media3 1.4.1 và bỏ `kohii-androidx`).

Mức ưu tiên: **P0** là lỗi hoặc thứ đang hỏng, **P1** nên làm sớm, **P2** dọn dẹp khi có thời gian.

## 1. Lỗi thật trong code

### P0. `PlayerParameters` không được áp vào player mới (đã sửa)

`kohii-exoplayer/.../PlayerViewBridge.kt`, hàm `ensurePlayer()`:

```kotlin
val player = playerPool.getPlayer(this.media)
applyPlayerParameters(playerParameters) // this.player vẫn đang null -> return ngay
this.player = player
```

`applyPlayerParameters` đọc `this.player`, nhưng lúc đó field chưa được gán nên hàm thoát luôn.
Kết quả: giới hạn độ phân giải và bitrate (`maxVideoWidth/Height`, `maxVideoBitrate`,
`maxAudioBitrate`) mà client đặt qua `Playback.playerParameters` hoặc `NetworkTypeChangeListener`
bị bỏ qua mỗi khi bridge lấy player mới từ pool, cho tới lần đổi tham số kế tiếp.

Sửa: gán `this.player = player` trước rồi mới gọi `applyPlayerParameters`.

### P1. `PlaybackInfo` là data class có thể thay đổi (mutable) nhưng được chia sẻ

`PlaybackInfo` có `var resumeWindow` và `var resumePosition`. Bridge trả về chính object nội bộ
qua getter `playbackInfo`, và `seekTo()` sửa trực tiếp object đó. Object này còn được lưu làm
value trong `Master.playbackInfoStore`. Bất kỳ ai giữ tham chiếu đều có thể làm hỏng trạng thái
của bên khác.

Sửa: đổi thành `val` và dùng `copy(...)`. Việc này đổi API nên nên làm cùng đợt tăng version lớn.

### P1. `Playback.Token` giữ tham chiếu tới `Rect` dùng lại

`Playback.updateToken()` truyền `tmpRect` (một field dùng chung) vào `Token`. Token cũ vì thế bị
thay đổi theo mỗi lần refresh. Hiện chưa gây lỗi vì mỗi Playback chỉ đọc token mới nhất, nhưng
rất dễ thành lỗi khi có code giữ token cũ để so sánh.

## 2. Hiệu năng

### P1. Log debug vẫn tốn chi phí ở bản release (đã sửa)

Mọi lệnh log có dạng `"Playback#onRefresh $this".logDebug()`. Chuỗi được ghép **trước** khi vào
hàm, rồi mới kiểm tra `BuildConfig.DEBUG`. Vì vậy ngay cả bản release vẫn gọi `toString()` và
ghép chuỗi.

Chi phí đáng kể nhất nằm ở các đường chạy mỗi lần refresh (tức là liên tục khi scroll):

- `Playback.onRefresh()` và `updateToken()`: 3 chuỗi cho mỗi Playback. `Playback.toString()` lại
  gọi `Playable.toString()` và `Token.toString()`, tức là in cả `Rect`.
- `Playback.compareWith()` được gọi **bên trong comparator** khi sort ứng viên, nên tốn
  O(n log n) lần ghép chuỗi cho mỗi lần refresh.
- `PlayableDispatcher.play/pause` (`logWarn`), các callback của `AbstractPlayable`.

Sửa: đổi các hàm log thành `inline` nhận lambda, ví dụ
`internal inline fun logDebug(msg: () -> String) { if (BuildConfig.DEBUG) Log.d(TAG, msg()) }`,
và xóa lệnh log trong `compareWith`. Đây là thay đổi lớn về số dòng nhưng an toàn, và lợi ích rõ
nhất trong danh sách này.

### P1. Mỗi player có một `DefaultBandwidthMeter` riêng (đã sửa)

`ExoPlayerConfig.createBandwidthMeter()` tạo bandwidth meter mới cho mỗi player trong pool. Mỗi
player phải tự ước lượng băng thông lại từ đầu, nên video đầu tiên của mỗi player thường chọn sai
chất lượng (adaptive streaming). Với feed nhiều video, đây là lý do chất lượng thấp lúc mới vào.

Sửa: dùng một bandwidth meter dùng chung cho cả pool, ví dụ
`DefaultBandwidthMeter.getSingletonInstance(context)` khi client không cấu hình gì đặc biệt.

### P2. Pool có thể giữ quá nhiều ExoPlayer

`PlayerPool.DEFAULT_POOL_SIZE = max(SDK_INT / 6, số nhân CPU)`. Trên Android 14 với CPU 8 nhân,
pool có thể giữ tới 8 instance ExoPlayer đang rảnh. Mỗi instance có thread playback, renderer và
buffer riêng. Công thức `SDK_INT / 6` không còn ý nghĩa (nó được viết cho API 16–18).

Thực tế pool chỉ đầy khi nhiều player được tạo cùng lúc (bật `preload`, lưới nhiều video), và
player rảnh đã `stop()` thì decoder đã được giải phóng. Lợi ích chủ yếu ở máy yếu.

Sửa: đặt mặc định cố định nhỏ (2–3) và cho client cấu hình.

### P2. Refresh bị hoãn liên tục trong lúc scroll

`Group.onRefresh()` xóa message cũ rồi hẹn lại sau khoảng 33 ms mỗi lần có sự kiện scroll. Khi
fling liên tục, refresh chỉ chạy khi scroll dừng hoặc chậm lại, nên video bắt đầu phát trễ. Đây là
debounce; nếu muốn phản hồi nhanh hơn thì chuyển sang throttle (chạy tối đa một lần mỗi N ms).

### P2. Cấp phát bộ nhớ không cần thiết trên đường refresh

- `Group.refresh()`: `managers.flatMap`, `linkedSetOf`, `arraySetOf`,
  `toPause + toPlay + oldSelection - newSelection` (tạo 3 set tạm), `groupBy`.
- `Manager.refreshPlaybackStates()`: duyệt `playbacks` 3 lần qua `filterValues` và `filter`.
- `Manager.splitPlaybacks()`: `groupBy` và sort lại mỗi lần.
- `Playback.updateToken()`: tạo `Rect()` mới mỗi lần.

Mỗi chỗ nhỏ, nhưng tất cả chạy liên tục khi scroll. Nên gom thành một vòng lặp và dùng lại
collection.

### P2. `Master.playbackInfoStore` không giới hạn kích thước

Với Playable có tag, `PlaybackInfo` được lưu theo tag và chỉ bị xóa khi restore. Trong feed rất
dài, map này lớn dần suốt vòng đời app. Nên dùng `LruCache`.

### P2. Bật/tắt audio focus mỗi lần đổi âm lượng

`KohiiExoPlayer.setVolumeInfo()` gọi `setAudioAttributes(..., handleAudioFocus = !muted)` mỗi lần
đổi volume, kể cả khi trạng thái mute không đổi. Chỉ nên gọi khi chuyển qua lại giữa mute và
không mute.

## 3. Tech debt trong code

- **Thư viện hiển thị Toast**: `PlayerViewBridge` tự hiện `Toast` khi không có decoder hoặc track
  không hỗ trợ (P1). Thư viện không nên tự hiện UI; nên chỉ báo qua `ErrorListener` và để app quyết
  định.
- **`playbackParams` không bao giờ thay đổi**: `PlayerViewBridge.playbackParams` luôn là
  `PlaybackParameters.DEFAULT`, nên không có cách đặt tốc độ phát qua Kohii.
- **Code comment còn sót**: `ControlDispatcher` (đã bị xóa khỏi ExoPlayer từ 2.16) còn nằm trong
  `PlayerViewPlayable`, `DevScrollViewFragment`; logic 1.1.x trong `Bucket.selectByOrientation`;
  nhiều `TODO`.
- **API deprecated còn public**: `PlayerEventListener`, các hằng số trong `Common`
  (`REPEAT_MODE_*`, `STATE_*`). Có thể xóa ở lần tăng version lớn.
- **`AtomicReference` không cần thiết**: `Master.manuallyStartedPlayable` chỉ được dùng trên main
  thread.
- **`kohii-ads` đã chết**: module bị tắt trong `settings.gradle.kts`, vẫn dùng
  `setAdsLoaderProvider` (deprecated trong Media3). Cần viết lại bằng
  `setLocalAdInsertionComponents` hoặc xóa hẳn.
- **`kohii-experiments`**: dùng thư viện YouTube player không chính thức bản 11.1.0 (có `TODO`
  nâng lên 12) và xuất nó qua `api(...)`.
- **Gần như không có test**: `kohii-core` chỉ có `PlayerPoolTest`. Các logic quan trọng
  (`Manager.splitPlaybacks`, `Bucket.selectByOrientation`, lifecycle của `Playable`) chưa có test.

## 4. Build, CI và phát hành

- **P0. CI đang hỏng**: workflow dùng `actions/cache@v2`, đã bị GitHub ngừng hỗ trợ (job thất bại
  ngay). `actions/checkout@v1/v2`, `actions/setup-java@v1`, `gradle/wrapper-validation-action@v1`
  đều đã cũ. Nâng lên `checkout@v4`, `setup-java@v4`, `gradle/actions/setup-gradle@v4`.
- **P0. Kênh phát hành đã ngừng**: `deploy_snapshot.yml` publish qua Sonatype OSSRH bằng
  `gradle-nexus` publish plugin. OSSRH đã ngừng hoạt động (giữa 2025), cần chuyển sang
  Central Portal (ví dụ plugin `com.vanniktech.maven.publish` bản mới).
- **P1. Repository chết**: `build.gradle` còn `maven.fabric.io` (Fabric đã đóng cửa). Gradle vẫn
  thử repo này khi resolve dependency nên có thể làm build chậm hoặc lỗi.
- **P1. Công cụ cũ**: AGP 8.4.1, Kotlin 1.8.10, Gradle 8.7, ktlint-gradle 12.1.1. Nâng lên giúp
  build nhanh hơn và cho phép dùng Media3 bản mới hơn 1.4.1 (1.5+ yêu cầu compileSdk 35).
- **P1. Jetifier**: `android.enableJetifier=true` làm build chậm, có thể không còn cần (không còn
  thư viện support cũ). Cần kiểm tra bằng `./gradlew checkJetifier` rồi tắt.
- **P2. Dọn version catalog**: `libs.versions.toml` còn nhiều mục không dùng: `agp = 7.4.2` (AGP
  thật lấy từ `gradle = 8.4.1`), các mục Compose, Epoxy, KSP, coroutines, và một số module Media3
  (`rtsp`, `workmanager`, `decoder`, `extractor`). `buildSrc/.../Dependencies.kt` là code chết còn
  tham chiếu ExoPlayer 2 và Media2.
- **P2. Version không đồng bộ**: `gradle.properties` ghi `VERSION_NAME=1.4.0.2017001`, còn
  `libs.versions.toml` ghi `1.5.0.2018005-SNAPSHOT`. Cách đặt tên version theo số ExoPlayer
  (`2018005` = ExoPlayer 2.18.5) không còn đúng với Media3.
- **P2. Javadoc bị tắt** (`tasks.withType(Javadoc).all { enabled = false }`), và lint đang tắt
  `RestrictedApi`, `VisibleForTests`, `UnusedResources`, `UnusedIds` ở mọi module.
- **P2. File `.api`**: cần chạy `./gradlew apiDump` sau khi đổi sang Media3 (các file đã được sửa
  tay, phần do Kotlin tự sinh khi delegate interface có thể chưa khớp).

## 5. Thứ tự đề xuất

1. ~~Sửa lỗi `applyPlayerParameters`.~~ Đã xong.
2. Sửa CI để có lại kiểm tra tự động, chạy `apiDump`.
3. ~~Log dạng lambda, bandwidth meter dùng chung.~~ Đã xong. Còn lại: giảm kích thước pool.
4. Nâng AGP/Kotlin, rồi nâng Media3 lên bản mới nhất.
5. Dọn code chết, version catalog, quyết định số phận `kohii-ads`.
