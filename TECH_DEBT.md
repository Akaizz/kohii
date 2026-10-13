# Review: tech debt và hiệu năng

Phạm vi: `kohii-core`, `kohii-exoplayer`, `kohii-experiments`, cấu hình build và CI, trên nhánh
`media3` (sau khi chuyển sang Media3 1.4.1, bỏ `kohii-androidx` và `kohii-ads`).

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

### P1. Crash khi app dùng Media3 mới hơn bản Kohii được build

`KohiiExoPlayer` cài đặt `ExoPlayer` bằng delegation của Kotlin (`ExoPlayer by player`), và
`HybridMediaItem` làm tương tự với `MediaSource`. Kotlin chỉ sinh các hàm chuyển tiếp cho những
hàm có trong interface **tại thời điểm build Kohii** (Media3 1.4.1).

Media3 thường thêm hàm mới vào `Player`/`ExoPlayer` ở các bản sau. Vì `media3-exoplayer` là
dependency `api`, app rất dễ nâng Media3 lên bản mới hơn. Khi đó, nếu code của Media3 (ví dụ
`PlayerView`) hoặc của app gọi một hàm mới trên `KohiiExoPlayer`, app sẽ crash với
`AbstractMethodError`. Mọi player trong `ExoPlayerPool` đều là `KohiiExoPlayer`, nên rủi ro này
áp dụng cho toàn bộ feed.

Sửa: không cài đặt lại interface của Media3. Ví dụ dùng thẳng instance `ExoPlayer` do
`ExoPlayer.Builder` tạo ra và đặt logic âm lượng (`VolumeInfoController`) ở một lớp bọc riêng
không implement `ExoPlayer`; hoặc kế thừa `ForwardingPlayer` (được build cùng Media3 nên luôn
khớp). `HybridMediaItem` không được dùng ở đâu trong repo, có thể deprecate rồi xóa. Trong lúc
chưa sửa, cần ghi rõ cho client: phải dùng đúng bản Media3 mà Kohii được build.

### P2. `PlaybackInfo` là data class có thể thay đổi (mutable) nhưng được chia sẻ

`PlaybackInfo` có `var resumeWindow` và `var resumePosition`. Getter `playbackInfo` của bridge có
thể trả về chính object nội bộ (khi player đang idle), và `seekTo()` sửa trực tiếp object nhận
được. Khi restore, object lấy từ `Master.playbackInfoStore` được gán thẳng làm trạng thái của
bridge. Hiện chưa thấy lỗi cụ thể, nhưng ai giữ tham chiếu cũng có thể vô tình sửa trạng thái của
bên khác.

Sửa: đổi thành `val` và dùng `copy(...)`. Việc này đổi API nên nên làm cùng đợt tăng version lớn.

### P2. `Playback.Token` giữ tham chiếu tới `Rect` dùng lại

`Playback.updateToken()` truyền `tmpRect` (field dùng lại của mỗi Playback) vào `Token`. Token cũ
vì thế bị thay đổi theo mỗi lần refresh. Bên trong Kohii chỉ đọc token mới nhất nên chưa gây lỗi,
nhưng `Token` được đưa ra ngoài qua `TokenUpdateListener`: client nào giữ token cũ sẽ thấy giá trị
bị đổi.

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

Đã sửa: các hàm log giờ là `inline` nhận lambda (`logDebug { "..." }`), nên ở bản release chuỗi
không được tạo. Lệnh log trong `compareWith` vẫn được giữ (dạng lambda); ở bản debug nó vẫn tốn
chi phí khi sort.

### P1. Mỗi player có một `DefaultBandwidthMeter` riêng (đã sửa)

`ExoPlayerConfig.createBandwidthMeter()` tạo bandwidth meter mới cho mỗi player trong pool. Mỗi
player phải ước lượng băng thông lại từ giá trị đoán ban đầu, nên với stream đa chất lượng
(HLS/DASH) vài giây đầu của player mới dễ bị chọn chất lượng thấp hơn mức mạng cho phép. Không ảnh
hưởng tới MP4 (chỉ có một chất lượng).

Đã sửa: mọi player tạo từ cùng một `ExoPlayerConfig` dùng chung một meter (là
`DefaultBandwidthMeter.getSingletonInstance(context)` khi các tham số băng thông là mặc định).

### P2. Kích thước pool ExoPlayer và bộ nhớ thấp

`PlayerPool.DEFAULT_POOL_SIZE = max(SDK_INT / 6, số nhân CPU)`. Trên Android 14 với CPU 8 nhân,
giới hạn là 8 player rảnh. Công thức `SDK_INT / 6` không còn ý nghĩa (nó được viết cho API 16–18).

Trên thực tế đây **không phải vấn đề hiệu năng đáng lo**:

- Pool chỉ giữ tối đa số player từng chạy cùng lúc; feed thông thường chỉ khoảng 2–4.
- Player rảnh đã `stop()` nên decoder đã được giải phóng, chỉ còn một thread đang ngủ.
- Ngược lại, đặt pool quá nhỏ (1–2) sẽ làm player bị hủy và tạo lại liên tục trên main thread khi
  scroll, gây giật. **Không nên giảm pool để "tối ưu".**

Hai việc nên làm:

- Cho phép cấu hình kích thước pool qua `ExoPlayerConfig` (hiện `createKohii(context, config)`
  luôn dùng giá trị mặc định).
- Giải phóng player rảnh trong pool khi hệ thống báo thiếu bộ nhớ. Hiện `Master` có nhận
  `onTrimMemory`, nhưng chỉ dùng để đổi `MemoryMode`; pool chỉ được dọn khi không còn Playable nào.

### P2. Refresh bị hoãn liên tục trong lúc scroll

`Group.onRefresh()` xóa message cũ rồi hẹn lại sau khoảng 33 ms mỗi lần có sự kiện scroll. Khi
fling liên tục, refresh chỉ chạy khi scroll dừng hoặc chậm lại. Đây là đánh đổi có chủ ý: không
khởi động video trong lúc lướt nhanh (đỡ tốn tài nguyên), đổi lại video bắt đầu phát trễ hơn một
chút sau khi dừng. Chỉ nên đổi sang throttle (chạy tối đa một lần mỗi N ms) nếu thấy video bắt
đầu chậm thật sự khi đo.

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

## 3. Tech debt trong code

- **Thư viện hiển thị Toast** (P1): `PlayerViewBridge.onTracksChanged()` luôn hiện `Toast` khi
  video hoặc audio có track không hỗ trợ, app không tắt được. (Toast khi lỗi decoder chỉ hiện nếu
  không có `ErrorListener` nào, mà `Playback` luôn được đăng ký làm listener, nên thực tế hiếm
  khi hiện.) Thư viện không nên tự hiện UI; nên chỉ báo qua listener và để app quyết định.
- **`playbackParams` không bao giờ thay đổi**: `PlayerViewBridge.playbackParams` luôn là
  `PlaybackParameters.DEFAULT`, nên không có cách đặt tốc độ phát qua Kohii.
- **Code comment còn sót**: `ControlDispatcher` (đã bị xóa khỏi ExoPlayer từ 2.16) còn nằm trong
  `PlayerViewPlayable`, `DevScrollViewFragment`; logic 1.1.x trong `Bucket.selectByOrientation`;
  nhiều `TODO`.
- **API deprecated còn public**: `PlayerEventListener`, các hằng số trong `Common`
  (`REPEAT_MODE_*`, `STATE_*`). Có thể xóa ở lần tăng version lớn.
- **`AtomicReference` không cần thiết**: `Master.manuallyStartedPlayable` chỉ được dùng trên main
  thread.
- **`kohii-experiments`**: dùng thư viện YouTube player không chính thức bản 11.1.0 (có `TODO`
  nâng lên 12) và xuất nó qua `api(...)`.
- **Gần như không có test**: `kohii-core` chỉ có `PlayerPoolTest`. Các logic quan trọng
  (`Manager.splitPlaybacks`, `Bucket.selectByOrientation`, lifecycle của `Playable`) chưa có test.

## 4. Build, CI và phát hành

- **P0. CI đang hỏng**: workflow dùng `actions/cache@v2`, đã bị GitHub ngừng hỗ trợ (job thất bại
  ngay). `actions/checkout@v1/v2`, `actions/setup-java@v1`, `gradle/wrapper-validation-action@v1`
  đều đã cũ. Nâng lên `checkout@v4`, `setup-java@v4`, `gradle/actions/setup-gradle@v4`.
- **P0. Kênh phát hành đã ngừng**: `deploy_snapshot.yml` publish qua Sonatype OSSRH
  (`oss.sonatype.org` trong `gradle/release_maven.gradle`, cùng `gradle-nexus` publish plugin). OSSRH đã ngừng hoạt động (giữa 2025), cần chuyển sang
  Central Portal (ví dụ plugin `com.vanniktech.maven.publish` bản mới).
- **P1. Repository chết hoặc không cần**: `build.gradle` còn `maven.fabric.io` (Fabric đã đóng
  cửa) và `jitpack.io` (không còn dependency nào cần). Gradle vẫn thử các repo này khi resolve
  dependency nên làm build chậm, và JitPack là rủi ro về chuỗi cung ứng.
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
3. ~~Log dạng lambda, bandwidth meter dùng chung.~~ Đã xong. (Cũng đã làm thêm: buffer mặc định
   12 giây và cache LRU lớn hơn, xem `CHANGELOG.md`.)
4. Bỏ delegation trong `KohiiExoPlayer`/`HybridMediaItem`, **trước khi** nâng Media3.
5. Nâng AGP/Kotlin, rồi nâng Media3 lên bản mới nhất.
6. Dọn code chết và version catalog.
