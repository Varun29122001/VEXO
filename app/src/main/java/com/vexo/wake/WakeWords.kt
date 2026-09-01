package com.vexo.wake

/**
 * The phrases that wake VEXO, expressed as the BPE pieces the keyword spotter expects.
 *
 * The gigaspeech spotter is a transducer over byte-pair tokens, so a keyword is supplied as its
 * tokenisation rather than its spelling. These were produced by running the phrases through the
 * `bpe.model` shipped inside the model pack, and every piece was checked against the pack's
 * `tokens.txt` — "vexo" is not an English word, so this could not be assumed.
 *
 *     HEY VEXO    ->  ▁HE Y ▁ VE X O
 *     HI VEXO     ->  ▁HI ▁ VE X O
 *     OK VEXO     ->  ▁O K ▁ VE X O
 *     HELLO VEXO  ->  ▁HE LL O ▁ VE X O
 *
 * These mirror the wake phrases `CommandParser` already tolerates at the front of a request.
 */
internal object WakeWords {

    val phrases = listOf(
        "hey vexo" to "\u2581HE Y \u2581 VE X O",
        "hi vexo" to "\u2581HI \u2581 VE X O",
        "ok vexo" to "\u2581O K \u2581 VE X O",
        "hello vexo" to "\u2581HE LL O \u2581 VE X O",
    )

    /** Contents of the keywords file handed to the spotter: one tokenisation per line. */
    fun keywordsFileContent(): String =
        phrases.joinToString(separator = "\n", postfix = "\n") { (_, tokens) -> tokens }
}
