package com.vexo.voice

import com.vexo.models.RemoteModel
import com.vexo.models.VexoModels

/**
 * A neural voice pack: which archive to fetch, plus the bits of it the synthesiser needs to be told
 * about. Swapping packs is a data change rather than a code change.
 *
 * Which *speaker* within the pack is used is not fixed here — it is a user setting, because
 * `libritts_r` is multi-speaker. See [VoiceOption] and `VexoSettings.speakerId`.
 */
data class VoiceModel(
    val pack: RemoteModel.Archive,
    /** Weights file name inside the pack. */
    val weightsFileName: String,
    /** Recorded so the licence story stays attached to the artefact. See README. */
    val datasetLicence: String,
) {
    val id: String get() = pack.id

    companion object {

        /**
         * Piper VITS, en_US, 904 speakers, 22.05 kHz, 19.5 M parameters.
         *
         * Chosen over the more commonly demoed `en_US-lessac-medium` because Lessac's Blizzard 2013
         * dataset carries a research-only licence that excludes commercial voice synthesis.
         * LibriTTS-R is CC BY 4.0. Note the weights were still fine-tuned from the Lessac voice, so
         * the provenance chain is not completely clean — see README.
         */
        val LibriTtsR = VoiceModel(
            pack = VexoModels.NeuralVoice,
            weightsFileName = "en_US-libritts_r-medium.onnx",
            datasetLicence = "CC BY 4.0 (LibriTTS-R, openslr.org/141)",
        )
    }
}
