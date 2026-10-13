# Review: tech debt và hiệu năng

Phạm vi: `kohii-core`, `kohii-exoplayer`, hai app sample, cấu hình build và CI, trên nhánh `media3`
(Media3 1.10.1; đã bỏ `kohii-androidx`, `kohii-ads`, `kohii-experiments`).

Mức ưu tiên: **P0** là lỗi hoặc thứ đang hỏng, **P1** nên làm sớm, **P2** dọn dẹp khi có thời gian.

> Lưu ý: nhánh này chưa được build lần nào sau khi đổi sang Media3 1.10.1 và bộ công cụ mới (môi
> trường viết review không có Android SDK). Lần build đầu tiên có thể còn lỗi biên dịch cần sửa.

## 0. Đã xử lý trên nhánh `media3`

- Chuyển từ ExoPlayer 2 sang Media3 1.10.1, rà các API dùng trong thư viện theo mã nguồn 1.10.1
  (chỉ có `NetworkTypeObserver.register(Listener)` bị deprecated, đã đổi sang bản có `Executor`).
- `PlayerViewBridge.ensurePlayer()` gọi `applyPlayerParameters()` trước khi gán player, nên giới hạn
  độ phân giải/bitrate bị bỏ qua với mọi player lấy từ pool. Đã sửa.
- Log debug ghép chuỗi (và gọi `toString()`) cả ở bản release, kể cả trên đường refresh khi scroll.
  Đã đổi sang hàm `inline` nhận lambda.
- Mỗi player trong pool có một `DefaultBandwidthMeter` riêng (HLS/DASH phải ước lượng lại băng
  thông). Đã dùng chung một meter cho mỗi `ExoPlayerConfig`.
- Buffer mặc định 50 giây → 12 giây; cache LRU 24 MB → 10% dung lượng trống (32–256 MB), chuyển
  sang `cacheDir`.
- Build: AGP 8.9.2, Gradle 8.13, Kotlin 2.1.0 (cùng phiên bản với app), compileSdk 36, minSdk 24,
  targetSdk (sample) 36;
  bỏ repo chết (`maven.fabric.io`, `jitpack.io`, snapshot của OSSRH); bỏ các mục không dùng trong
  version catalog; đổi DSL deprecated (`compileSdkVersion`, `lintOptions`, `packagingOptions`,
  `kotlinOptions`, `archivesBaseName`); thêm `publishing { singleVariant('release') }` mà AGP 8 bắt
  buộc để có component `release` cho `gradle/release_maven.gradle`.
- CI: thay hai workflow cũ (dùng `actions/cache@v2` đã bị GitHub ngừng hỗ trợ) bằng `ci.yml` chạy
  `./gradlew check assembleDebug` với `checkout@v4`, `setup-java@v4`, `gradle/actions/setup-gradle@v4`.
  Bỏ workflow publish snapshot lên Sonatype OSSRH (đã ngừng hoạt động).
- Phát hành qua JitPack thay cho OSSRH (group `com.github.akaizz.kohii`); `kohii-exoplayer` khai báo
  `kohii-core` dạng `api` nên app chỉ cần khai báo `kohii-exoplayer`.

## 1. Rủi ro và lỗi trong code

### P1. Phiên bản Media3 của app phải trùng với bản Kohii được build

`KohiiExoPlayer` cài đặt `ExoPlayer` bằng delegation của Kotlin (`ExoPlayer by player`). Kotlin chỉ
sinh hàm chuyển tiếp cho các hàm có trong `ExoPlayer` **tại thời điểm build Kohii**. Nếu app dùng
Media3 mới hơn (Gradle luôn chọn bản cao nhất trong cả app), gọi một hàm mới thêm vào `ExoPlayer`
trên `KohiiExoPlayer` sẽ crash với `AbstractMethodError`.

Bằng chứng (đối chiếu mã nguồn `androidx/media`):

- Từ 1.4.1 lên 1.8.0, interface `Player` không thêm hàm nào (chỉ xóa 8 hàm deprecated), nhưng
  `ExoPlayer` thêm 5 hàm abstract: `setScrubbingModeEnabled`, `isScrubbingModeEnabled`,
  `setScrubbingModeParameters`, `getScrubbingModeParameters`, `getShuffleOrder`.
- `PlayerControlView` bản 1.8.0, trong `updateProgress()`, gọi `isScrubbingModeEnabled()` qua
  reflection lên mọi player là `ExoPlayer`: màn nào hiện thanh điều khiển sẽ crash nếu
  `KohiiExoPlayer` được build với Media3 cũ hơn.

**Không bỏ được `KohiiExoPlayer`, cũng không nên đổi nó sang `ForwardingPlayer`**: client tạo
trực tiếp `KohiiExoPlayer(...)` trong `PlayerPool` riêng và kiểm tra `player is ExoPlayer` (để trả
`AudioAttributes` về mặc định khi reset). Đổi sang `ForwardingPlayer` sẽ làm kiểm tra đó âm thầm sai.

Cách xử lý: quy định nâng Media3 ở Kohii và app cùng lúc (hiện cùng là 1.10.1). App nên khai báo
mọi module Media3 (kể cả `media3-datasource-okhttp`, `media3-exoplayer-hls`) bằng cùng một biến
version.

### P2. `HybridMediaItem` không còn tác dụng

Nó được thiết kế để app đưa vào một `MediaSource` dựng sẵn: `DefaultMediaSourceFactoryProvider`
kiểm tra `media is HybridMediaItem` và dùng thẳng `media.mediaSource`. Commit `fdbb72d` (nâng
ExoPlayer 2.14) đã xóa provider đó, nên từ đó `MediaSource` truyền vào bị bỏ qua. Class này còn cài
đặt lại interface `MediaSource`, nên có cùng rủi ro lệch phiên bản như trên. Nên deprecate rồi xóa.

### P2. `PlaybackInfo` là data class có thể thay đổi nhưng được chia sẻ

`PlaybackInfo` có `var resumeWindow`, `var resumePosition`. Getter `playbackInfo` của bridge có thể
trả về chính object nội bộ, `seekTo()` sửa trực tiếp object nhận được, và khi restore object lấy từ
`Master.playbackInfoStore` được gán thẳng làm trạng thái bridge. Chưa thấy lỗi cụ thể. Đổi sang
`val` + `copy()` là thay đổi API, nên gộp vào lần tăng version lớn.

### P2. `Playback.Token` giữ tham chiếu tới `Rect` dùng lại

`Playback.updateToken()` truyền `tmpRect` (field dùng lại) vào `Token`, nên token cũ bị thay đổi ở
lần refresh sau. Bên trong Kohii chỉ đọc token mới nhất, nhưng `Token` được đưa ra ngoài qua
`TokenUpdateListener`.

## 2. Hiệu năng

### P2. Pool ExoPlayer không được dọn khi thiếu bộ nhớ

`Master` nhận `onTrimMemory` nhưng chỉ dùng để đổi `MemoryMode`; player rảnh trong pool chỉ được hủy
khi không còn Playable nào. Nên giải phóng player rảnh khi `onLowMemory`. Không nên dọn ở
`TRIM_MEMORY_UI_HIDDEN` (mỗi lần chuyển app), vì khi quay lại phải tạo lại player.

Kích thước pool (`max(SDK_INT / 6, số nhân CPU)`) **không phải vấn đề**: pool chỉ giữ tối đa số
player từng chạy cùng lúc, player rảnh đã `stop()` nên decoder đã được giải phóng. Giảm pool quá nhỏ
còn làm player bị tạo lại liên tục khi scroll. Có thể cho cấu hình `poolSize` qua `ExoPlayerConfig`
(hiện `createKohii(context, config)` luôn dùng mặc định).

### P2. `MemoryMode.LOW` không còn được kích hoạt trên Android 14+

`Master.preferredMemoryMode()` chuyển sang `LOW` khi `trimMemoryLevel >= TRIM_MEMORY_RUNNING_LOW`.
Hằng số này bị deprecated từ API 34 và hệ thống không còn gửi các mức `TRIM_MEMORY_RUNNING_*` cho
app. Cần dựa vào tín hiệu khác (ví dụ `onLowMemory`, `ActivityManager.isLowRamDevice()`).

### P2. Refresh bị hoãn trong lúc scroll

`Group.onRefresh()` xóa message cũ rồi hẹn lại sau khoảng 33 ms mỗi lần có sự kiện scroll, nên khi
fling liên tục refresh chỉ chạy khi scroll chậm lại. Đây là đánh đổi có chủ ý (không khởi động video
khi lướt nhanh). Chỉ nên đổi sang throttle nếu đo thấy video bắt đầu chậm.

### P2. Cấp phát bộ nhớ trên đường refresh

`Group.refresh()` (`flatMap`, các set tạm, `groupBy`), `Manager.refreshPlaybackStates()` (duyệt
`playbacks` 3 lần), `Manager.splitPlaybacks()` (`groupBy`, sort), `Playback.updateToken()` (`Rect()`
mới mỗi lần). Mỗi chỗ nhỏ nhưng chạy liên tục khi scroll. Chỉ nên tối ưu khi đo thấy cần.

### P2. `Master.playbackInfoStore` không giới hạn kích thước

Với Playable có tag, `PlaybackInfo` chỉ bị xóa khi được restore. Feed rất dài sẽ làm map này lớn dần.
Nên dùng `LruCache`.

## 3. Tech debt trong code

- **P1. Thư viện tự hiện Toast**: `PlayerViewBridge.onTracksChanged()` luôn hiện `Toast` khi video
  hoặc audio có track không hỗ trợ, app không tắt được. (Toast khi lỗi decoder chỉ hiện nếu không có
  `ErrorListener`; `Playback` luôn được đăng ký nên thực tế hiếm khi hiện.) Nên báo qua listener để
  app quyết định; đây là thay đổi hành vi, cần ghi CHANGELOG.
- **P2. `playbackParams` không bao giờ thay đổi**: `PlayerViewBridge.playbackParams` luôn là
  `PlaybackParameters.DEFAULT`, không có cách đặt tốc độ phát qua Kohii.
- **P2. Code comment còn sót**: `ControlDispatcher` (đã bị xóa khỏi ExoPlayer từ 2.16) trong
  `PlayerViewPlayable` và một số màn sample; logic 1.1.x trong `Bucket.selectByOrientation`.
- **P2. API deprecated còn public**: `PlayerEventListener`, các hằng số trong `Common`
  (`REPEAT_MODE_*`, `STATE_*`), các type alias `StyledPlayerView*`. Có thể xóa ở lần tăng version lớn.
- **P2. Gần như không có test**: chỉ có `PlayerPoolTest`. Logic chọn video
  (`Manager.splitPlaybacks`, `Bucket.selectByOrientation`) và vòng đời `Playable` chưa có test.

## 4. Build, CI và phát hành

- **P0. Cập nhật file `.api`**: chạy `./gradlew apiDump` và commit. Các file `.api` đang được sửa
  tay (phần do Kotlin sinh cho `KohiiExoPlayer` không khớp), nên `apiCheck` trong CI sẽ thất bại cho
  tới khi dump lại.
- **P1. Kiểm tra bản build JitPack đầu tiên**: thư viện được phát hành qua JitPack
  (`com.github.akaizz.kohii:kohii-core`/`kohii-exoplayer`, cấu hình trong `jitpack.yml`). Cần mở
  `https://jitpack.io/#akaizz/kohii`, build một tag hoặc `media3-SNAPSHOT` và xem log để chắc JitPack
  build được với JDK 17, compileSdk 36. Sau đó đổi app từ `im.ene.kohii:*:1.4.0.2017001` (bản gốc,
  ExoPlayer 2.17.1) sang bản này, sửa `FeedVideoPlayerInitializer` sang Media3, rồi bỏ các dependency
  `com.google.android.exoplayer:*:2.17.1` nếu không còn chỗ nào dùng.
- **P2. Jetifier vẫn bật** (`android.enableJetifier=true`) dù có thể không còn thư viện support cũ;
  làm build chậm. Kiểm tra bằng `./gradlew checkJetifier` rồi tắt.
- **P2. Version không đồng bộ**: `gradle.properties` ghi `VERSION_NAME=1.4.0.2017001`,
  `libs.versions.toml` ghi `1.5.0.2018005-SNAPSHOT`. Cách đặt version theo số ExoPlayer
  (`2018005` = 2.18.5) không còn đúng với Media3.
- **P2. Javadoc bị tắt** ở `kohii-core`, và lint tắt `RestrictedApi`, `VisibleForTests`,
  `UnusedResources`, `UnusedIds` ở mọi module.
- **P2. Code chết**: `buildSrc/.../Dependencies.kt` không được dùng, còn tham chiếu ExoPlayer 2 và
  Media2. `.github/FUNDING.yml` trỏ tới tài khoản tác giả gốc.
- **P2. Sample target SDK 36**: từ Android 15 app bắt buộc edge-to-edge (Android 16 bỏ luôn cách tắt),
  giao diện sample có thể bị thanh hệ thống che.

## 5. Thứ tự đề xuất

1. Build nhánh `media3`, sửa lỗi biên dịch nếu có, chạy `./gradlew apiDump` và commit.
2. Build bản JitPack đầu tiên và chuyển app sang dùng nó.
3. Bỏ Toast tự hiện trong `PlayerViewBridge`.
4. Dọn pool khi thiếu bộ nhớ, sửa `MemoryMode.LOW` trên Android 14+.
5. Viết test cho logic chọn video trước khi tối ưu đường refresh.
6. Dọn code chết, API deprecated, version name.
