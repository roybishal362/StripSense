package app.stripsense.core.domain

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
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

/** One more person whose medicines are kept on this phone: a husband, a grandmother. The first person is [Person]. */
@Serializable
data class Profile(val id: String, val callEn: String, val callHi: String = "", val isWoman: Boolean = false) {
    fun call(lang: String) = if (lang == "hi") callHi.ifEmpty { callEn } else callEn
}

/**
 * The people of one home on one phone. Every medicine belongs to one of them. The scan knows every strip in the
 * house, so it can say "this is Papa's, not yours": two people's strips kept in one box are easily swapped.
 */
object People {
    /** The id of the first person, the one the phone was set up for. */
    const val FIRST = ""

    fun all(data: StoreData): List<Profile> = listOf(Profile(FIRST, data.person.callEn, data.person.callHi, data.person.isWoman)) + data.people

    /** Whose turn it is on the phone now. A saved id that no longer exists falls back to the first person. */
    fun active(data: StoreData): String = data.activePerson.takeIf { id -> data.people.any { it.id == id } } ?: FIRST

    private fun ownerId(data: StoreData, med: Medicine): String = med.personId.takeIf { id -> data.people.any { it.id == id } } ?: FIRST

    fun of(data: StoreData, med: Medicine): Profile = all(data).first { it.id == ownerId(data, med) }

    fun medicines(data: StoreData, personId: String = active(data)): List<Medicine> = data.medicines.filter { ownerId(data, it) == personId }

    /** True when the strip belongs to somebody other than the person using the phone now. */
    fun someoneElses(data: StoreData, med: Medicine): Boolean = ownerId(data, med) != active(data)

    /** The profile in the shape the sentences and the week file take. */
    fun asPerson(data: StoreData, profile: Profile): Person =
        data.person.copy(callEn = profile.callEn, callHi = profile.callHi.ifEmpty { profile.callEn }, isWoman = profile.isWoman)
}

