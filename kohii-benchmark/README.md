# kohii-benchmark

Macrobenchmark đo hiệu năng của Kohii trên app `kohii-sample`, bằng màn hình feed video dọc
`kohii.v1.sample.benchmark.FeedBenchmarkActivity` (60 video, mỗi video cao 240dp).

## Đo những gì

`FeedScrollBenchmark.scrollFeed`: xóa dữ liệu app, mở feed, chờ video đầu phát, rồi lướt xuống
10 lần (mỗi lần 80% chiều cao feed, nghỉ 1,5 giây để Kohii chọn và phát video kế tiếp). Lặp lại 5
lần.

| Metric | Ý nghĩa |
|---|---|
| `frameDurationCpuMs` (P50/P90/P95/P99) | Thời gian CPU để vẽ mỗi frame khi lướt và phát video. |
| `frameOverrunMs` (API 31+) | Frame trễ so với hạn vẽ; số dương là bị giật. |
| `KohiiFirstFrameAverageMs` | Thời gian trung bình từ lúc một video được yêu cầu phát (buffering với playWhenReady) tới frame đầu tiên được vẽ. |
| `KohiiFirstFrameCount` | Số video đã vẽ được frame đầu trong một lần chạy. |

## Chạy

Cần một máy Android thật (API 29+ để có `KohiiFirstFrame*`), đã mở khóa màn hình. Không nên dùng
emulator vì số đo không ổn định.

Feed mặc định dùng video có sẵn trong `assets` của sample, nên không phụ thuộc mạng:

```bash
./gradlew :kohii-benchmark:connectedBenchmarkAndroidTest
```

Đo với video thật (MP4, m3u8...) thì truyền danh sách URL, cách nhau bằng dấu phẩy; danh sách được
lặp lại cho đủ 60 video:

```bash
./gradlew :kohii-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.feedUrls="https://cdn.example.com/a.mp4,https://cdn.example.com/b/master.m3u8"
```

Kết quả nằm trong `kohii-benchmark/build/outputs/connected_android_test_additional_output/`: file
JSON với từng metric, và file trace Perfetto (`*.perfetto-trace`) mở được bằng
<https://ui.perfetto.dev> để xem chi tiết từng frame và section `KohiiFirstFrame`.

## So sánh hai phiên bản

Chạy cùng một benchmark, trên cùng một máy, cùng danh sách URL, với hai phiên bản Kohii cần so
sánh, rồi so các metric ở trên. Một vài lưu ý để số đo có ý nghĩa:

- Chạy mỗi bên ít nhất 2 lần và chỉ tin những khác biệt lớn hơn độ dao động giữa các lần chạy.
- Đo với mạng thật sẽ dao động nhiều hơn; feed mặc định (video local) phù hợp để so độ mượt khi
  lướt, URL thật phù hợp để so thời gian tới frame đầu (bandwidth meter, buffer, cache).
- Build type `benchmark` của sample tắt R8 (`minifyEnabled false`); cả hai bên phải dùng cùng cấu
  hình này.
- Mỗi lần lặp đều xóa dữ liệu của app (`pm clear`) trước khi mở feed, nên cache video luôn trống và
  các lần lặp được đo trong cùng điều kiện.
