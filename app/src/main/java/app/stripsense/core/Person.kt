package app.stripsense.core

import kotlinx.serialization.Serializable

/** The person who uses the phone, and the family contact. Spec 03 section 0. */
@Serializable
data class Person(
    val callEn: String = "Maa",
    val callHi: String = "माँ",
    val isWoman: Boolean = true,
    val contactEn: String = "Rahul",
    val contactHi: String = "राहुल",
    val contactPhone: String = "",
    val pin: String = "1234",
) {
    fun call(lang: String) = if (lang == "hi") callHi else callEn
    fun contact(lang: String) = if (lang == "hi") contactHi else contactEn
}
