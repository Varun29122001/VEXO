package com.vexo.models

private const val TTS_RELEASE =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
private const val KWS_RELEASE =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models"
private const val SPEAKER_RELEASE =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models"

/**
 * The three network files the keyword spotter needs. Named here rather than in two places so the
 * download's required-file list and the spotter's configuration cannot drift apart — that drift is
 * exactly what produced a native abort during development.
 */
object WakeWordFiles {
    const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.onnx"
    const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
    const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
    const val TOKENS = "tokens.txt"
}

/**
 * Everything VEXO downloads. Sizes are exact and verified against the release assets, because
 * [ModelStore] uses them to resume and to reject a truncated transfer.
 *
 * Together these come to roughly 128 MiB on top of the APK. See the README for why each is needed
 * and what its licence implies.
 */
object VexoModels {

    /**
     * Piper VITS, en_US, 904 speakers, 22.05 kHz, 19.5 M parameters.
     *
     * Chosen over the more commonly demoed `en_US-lessac-medium` because Lessac's Blizzard 2013
     * dataset carries a research-only licence that excludes commercial voice synthesis.
     */
    val NeuralVoice = RemoteModel.Archive(
        id = "vits-piper-en_US-libritts_r-medium",
        url = "$TTS_RELEASE/vits-piper-en_US-libritts_r-medium.tar.bz2",
        bytes = 82_038_311L,
        required = listOf(
            "en_US-libritts_r-medium.onnx",
            "tokens.txt",
            "espeak-ng-data",
        ),
    )

    /**
     * Streaming zipformer keyword spotter, English, 3.3 M parameters.
     *
     * Note this is the full pack, not the smaller `-mobile` one. The mobile pack ships no float
     * joiner, which forces a float encoder to be paired with an int8 joiner, and that combination
     * aborts inside onnxruntime with a reshape mismatch at `/downsample/Reshape_1`
     * (`Input shape:{17,1,128}, requested shape:{8,2,1,128}`) the first time a chunk is decoded.
     * These are the same three files sherpa-onnx's own reference configuration uses. The 2 MiB
     * saving was not worth a native crash.
     */
    val WakeWord = RemoteModel.Archive(
        id = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01",
        url = "$KWS_RELEASE/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2",
        bytes = 17_626_723L,
        required = listOf(
            WakeWordFiles.ENCODER,
            WakeWordFiles.DECODER,
            WakeWordFiles.JOINER,
            "tokens.txt",
        ),
    )

    /**
     * CAM++ speaker embedding extractor covering English and Chinese, 16 kHz input. Produces a
     * fixed-length embedding per utterance; cosine similarity against an enrolled average decides
     * whether it is the same speaker.
     */
    val SpeakerEmbedding = RemoteModel.SingleFile(
        id = "3dspeaker-campplus-sv-zh-en-16k",
        url = "$SPEAKER_RELEASE/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        bytes = 28_281_164L,
        fileName = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
    )
}
