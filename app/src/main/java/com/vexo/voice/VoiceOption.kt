package com.vexo.voice

/**
 * A selectable voice within the multi-speaker pack.
 *
 * `libritts_r` carries 904 speakers, which is far too many to page through, so this is a curated
 * shortlist. Each [speakerId] is the pack's own `sid`, and [corpusSpeaker] is the LibriSpeech
 * speaker it corresponds to according to the pack's `speaker_id_map` — recorded so the choice is
 * traceable rather than magic.
 *
 * Genders were taken from the LibriSpeech speaker metadata rather than guessed. Quality still
 * varies between speakers, which is exactly why the settings screen can preview them.
 */
data class VoiceOption(
    val speakerId: Int,
    val label: String,
    val corpusSpeaker: String,
) {
    companion object {

        /**
         * Six male and four female voices spread across the pack.
         *
         * The previous default was `sid 109`, which is LibriSpeech speaker 6233 — female. It was
         * picked without listening to anything, which is why it needed changing.
         */
        val curated = listOf(
            VoiceOption(2, "Male 1", "4535"),
            VoiceOption(5, "Male 2", "922"),
            VoiceOption(6, "Male 3", "2531"),
            VoiceOption(8, "Male 4", "8848"),
            VoiceOption(19, "Male 5", "6458"),
            VoiceOption(24, "Male 6", "176"),
            VoiceOption(0, "Female 1", "3922"),
            VoiceOption(1, "Female 2", "8699"),
            VoiceOption(10, "Female 3", "3615"),
            VoiceOption(109, "Female 4", "6233"),
        )

        /** Default: a male voice, since the pack's arbitrary `sid 109` was female. */
        val default = curated.first()

        fun labelFor(speakerId: Int): String =
            curated.firstOrNull { it.speakerId == speakerId }?.label ?: "Speaker $speakerId"
    }
}
