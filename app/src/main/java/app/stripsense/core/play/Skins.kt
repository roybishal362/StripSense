package app.stripsense.core.play

/**
 * What Pattu looks like. Every one is the same Pattu, with the sprout on its head, dressed as something else.
 * Each is opened by reaching a level, so there is always one more to play for. All of them are the app's own
 * drawings.
 */
enum class Skin(val en: String, val hi: String, val level: Int) {
    SPROUT("Sprout", "अंकुर", 1), PUPPY("Puppy", "पिल्ला", 2), KITTEN("Kitten", "बिल्ली", 3), FAIRY("Fairy", "परी", 4), IMP("Little devil doctor", "नटखट डॉक्टर", 5);

    fun name(lang: String) = if (lang == "hi") hi else en
}

object Skins {
    fun open(skin: Skin, level: Int): Boolean = level >= skin.level

    /** The one the person chose, when their level has opened it; otherwise the first one. */
    fun of(chosen: String, level: Int): Skin = Skin.entries.firstOrNull { it.name == chosen && open(it, level) } ?: Skin.SPROUT

    /** The next one to play for, or null when all are open. */
    fun next(level: Int): Skin? = Skin.entries.firstOrNull { !open(it, level) }
}
