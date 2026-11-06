package uz.met.teacher.speech

data class ModelFile(val name: String, val url: String, val sizeBytes: Long, val sha256: String)

/** Downloaded on first use rather than shipped, to keep the app small. */
data class ModelPack(val folder: String, val titleUz: String, val files: List<ModelFile>) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }
}

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    private fun pack(folder: String, title: String, repo: String, vararg files: Triple<String, Long, String>) =
        ModelPack(folder, title, files.map { (name, size, sha) -> ModelFile(name, "$HF/$repo/resolve/main/$name", size, sha) })

    val whisperBase = pack(
        "whisper-base.en", "Nutqni tanish (base)", "csukuangfj/sherpa-onnx-whisper-base.en",
        Triple("base.en-encoder.int8.onnx", 29120534L, "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823"),
        Triple("base.en-decoder.int8.onnx", 130669978L, "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41"),
        Triple("base.en-tokens.txt", 835554L, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
    )

    val whisperSmall = pack(
        "whisper-small.en", "Nutqni tanish (small)", "csukuangfj/sherpa-onnx-whisper-small.en",
        Triple("small.en-encoder.int8.onnx", 112442483L, "8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f"),
        Triple("small.en-decoder.int8.onnx", 262223042L, "710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb"),
        Triple("small.en-tokens.txt", 835554L, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
    )

    val supertonic = pack(
        "supertonic", "Ovoz (Supertonic)", "csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        Triple("duration_predictor.int8.onnx", 3700147L, "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
        Triple("text_encoder.int8.onnx", 36416150L, "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
        Triple("vector_estimator.int8.onnx", 78400833L, "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
        Triple("vocoder.int8.onnx", 25991073L, "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
        Triple("tts.json", 8253L, "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
        Triple("unicode_indexer.bin", 262144L, "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
        Triple("voice.bin", 517168L, "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
    )

    val kokoro = pack(
        "kokoro", "Ovoz (Kokoro)", "csukuangfj/kokoro-int8-multi-lang-v1_0",
        Triple("model.int8.onnx", 114202993L, "75635ea3b85a32d00094dcdd61a258aba87c160f7c264c304a7b3ae9d734195e"),
        Triple("voices.bin", 28200960L, "1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29"),
        Triple("tokens.txt", 687L, "6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425"),
        Triple("lexicon-us-en.txt", 5956885L, "7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14"),
    )
}
