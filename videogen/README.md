# videogen abstraction layer

`videogen` unifies the asynchronous video generation protocols of Alibaba
Bailian Wan, Volcengine Ark Seedance and MiniMax H3.

## Design boundaries

All three APIs follow the same task lifecycle:

1. Submit a generation request and receive a task ID;
2. Poll the task status;
3. On success, fetch the time-limited video URL, and persist or download it
   promptly at the caller layer.

The module only adapts provider protocols; it does not persist tasks,
download videos, upload local assets or manage UI state.

Common models live in `model/VideoGeneration.kt`:

- `VideoGenerationRequest`: common fields such as prompt, multimodal inputs,
  resolution, aspect ratio, duration, audio, watermark;
- `VideoGenerationInput`: first/last frame, reference images/videos/audio,
  files, web pages and provider-raw inputs;
- `VideoGenerationTask`: unified queued/running/succeeded/failed/cancelled/
  expired states and results;
- `extraParameters` / `Raw`: escape hatches for fast-moving provider-specific
  fields, avoiding frequent changes to the common API.

`VideoGenerationProvider` exposes only `create` and `query`.
`VideoGenerationManager.watch` provides a cancellable polling Flow that the
UI or a task repository can collect directly.

## Capability differences

| Capability | Bailian Wan 3.0 | Volcengine Seedance 2.0 | MiniMax H3 |
|-----------|-----------------|-------------------------|------------|
| Text to video | Yes | Yes | Yes, prompt required |
| First/last frame | Yes | Yes | Yes |
| Reference image/video/audio | Yes | Yes | Yes |
| File/web input | Yes | No | No |
| Smart duration `-1` | Yes | Some models | No |
| Per-request callback URL | No, account-level async callback config | Yes | Yes |
| Audio output switch | Yes | Some models | Not exposed by API |

Duration, resolution, asset count and format limits change quickly across
models, so the common layer hardcodes no capability table; adapters perform
protocol-level validation only and the server remains the source of truth for
parameter validity.

## Usage example

```kotlin
val manager = VideoGenerationManager(okHttpClient)
val setting = VideoGenerationProviderSetting.MiniMax(apiKey = apiKey)

val submitted = manager.create(
    setting = setting,
    request = VideoGenerationRequest(
        prompt = "A retro sports car slowly cruising a neon-lit street on a rainy night",
        resolution = "2K",
        aspectRatio = "16:9",
        durationSeconds = 5,
    ),
).getOrThrow()

manager.watch(setting, submitted.id).collect { task ->
    // Persist or render the task; the Flow ends automatically on terminal state.
}
```

## Official documentation

- [Alibaba Bailian Wan 3.0](https://help.aliyun.com/zh/model-studio/wan3-video-generation-api-reference)
- [Volcengine Ark video generation API](https://www.volcengine.com/docs/82379/1520757)
- [MiniMax H3 create video task](https://platform.minimaxi.com/docs/api-reference/video-generation-v2-create)
- [MiniMax H3 query task](https://platform.minimaxi.com/docs/api-reference/video-generation-v2-query)
