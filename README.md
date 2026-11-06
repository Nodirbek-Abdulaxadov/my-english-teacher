# My English Teacher

An Android app for going from A2/B1 to B2 English by actually speaking. You talk with a tutor powered by Claude, and grammar, vocabulary and listening practice grow out of your own conversations. The interface and explanations are in Uzbek.

The app has no backend and no accounts. You use your own Claude API key.

## What it does

- **A learning path that teaches before it asks.** Thirteen units from A2 to B2. Before each role-play you learn the phrases, hear a model dialogue, repeat and drill, so you have the language ready. A placement test picks where you start.
- **Conversation and role-plays.** Tap the mic and talk. The tutor pitches its English at your level, and a button offers ready-made replies when you're stuck. It usually corrects by using the right form in its reply, asks you to repeat the mistakes that matter, and reviews the rest at the end of the session.
- **Say it better.** Hear how a B2 speaker would say what you just said.
- **Grammar** built from your repeated mistakes, practised out loud.
- **Vocabulary** from your conversations, reviewed out loud with FSRS spaced repetition.
- **Listening.** Generated dialogues at adjustable speed with questions, retelling, shadowing and dictation, or exercises built from your own audio file.
- **Habit.** A daily goal, XP that mostly rewards speaking, a streak with freezes, a learning path and a daily reminder.
- **Progress.** Speaking tests with a CEFR estimate, and charts of speed, pauses and errors per 100 words.

## Privacy

Speech recognition and speech synthesis run on the phone, so your voice never leaves it. Only text goes to Anthropic, under your own key. The key is encrypted with the Android Keystore. The app has no analytics or telemetry, and backups are turned off.

## Cost

Speech is free because it runs on the phone. You pay Anthropic for the text. These are estimates for one hour of practice a day, measure your own use in the app:

| Model | Per month |
|---|---|
| Opus 5.5 | ~$40 |
| Sonnet 5.5 | ~$23 |
| Haiku 5.5 | ~$1 |

Set a spend limit in the [Anthropic Console](https://console.anthropic.com) as well as the monthly limit in the app's settings.

## Getting started

Requirements: an arm64 Android 10+ phone, with about 400 MB free, and JDK 21 and the Android SDK to build.

```bash
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Without a `keystore.properties` file, the release build is signed with your debug key. On first launch the app asks for your API key, which you can get from the [Anthropic Console](https://console.anthropic.com/settings/keys), and downloads about 305 MB of speech models.

## Project layout

- `core`: plain Kotlin, no Android. Claude requests, prompts, spaced repetition, streak and XP rules, the learning path.
- `app`: the Android app. Compose UI, Room database, audio, on-device speech through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).
- `bench`: a stand-in for the Claude API used by the tests, and a benchmark that compares models (`tools/bench.sh`, spends a few cents).

```bash
./gradlew :core:test :bench:test
```

## Limitations

- Claude reads transcripts and can't hear you, so the pronunciation check is coarse: it shows which words the speech recognizer understood.
- The speech recognizer sometimes fixes a mistake on its own, and then the tutor never sees it. Settings > Diagnostics has a test that measures this for your voice.
- The CEFR level is an estimate, not a certificate.

## License

MIT for this project's code. The speech models aren't part of this repository: the app downloads them on first launch, and they carry their own licenses: [Whisper](https://github.com/openai/whisper) (MIT), [Supertonic](https://huggingface.co/supertone-oss-archive/supertonic-3/blob/main/LICENSE) (OpenRAIL-M, which restricts certain uses) and [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) (Apache 2.0).
