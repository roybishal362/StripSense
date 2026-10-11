package app.stripsense.core.teach

import app.stripsense.core.domain.*
import app.stripsense.core.text.*

/**
 * What a medicine is for, in the words an older person uses at home: "my BP medicine", "my sugar medicine".
 * A fixed list, so nothing the phone says about a purpose is free text written by a model.
 * [word]: the word that shows a nickname already says it ("BP tablet" needs no "BP medicine" in front).
 */
enum class PurposeKind(val en: String, val hi: String, val word: String) {
    FEVER_PAIN("fever and pain medicine", "बुख़ार और दर्द की दवा", "fever"),
    PAIN("pain medicine", "दर्द की दवा", "pain"),
    COLD("cold and fever medicine", "सर्दी-बुख़ार की दवा", "cold"),
    BP("BP medicine", "बीपी की दवा", "bp"),
    SUGAR("sugar medicine", "शुगर की दवा", "sugar"),
    CHOLESTEROL("cholesterol medicine", "कोलेस्ट्रॉल की दवा", "cholesterol"),
    BLOOD_THINNER("blood thinner", "खून पतला करने की दवा", "thinner"),
    HEART("heart medicine", "दिल की दवा", "heart"),
    THYROID("thyroid medicine", "थायरॉइड की दवा", "thyroid"),
    ACIDITY("acidity medicine", "एसिडिटी की दवा", "acidity"),
    NAUSEA("vomiting medicine", "उल्टी की दवा", "vomiting"),
    ALLERGY("allergy medicine", "एलर्जी की दवा", "allergy"),
    BREATHING("breathing medicine", "साँस की दवा", "breathing"),
    ANTIBIOTIC("antibiotic", "एंटीबायोटिक दवा", "antibiotic"),
    VITAMIN("vitamin", "विटामिन की दवा", "vitamin"),
    CALCIUM("calcium medicine", "कैल्शियम की दवा", "calcium"),
    IRON("iron medicine", "आयरन की दवा", "iron"),
    NERVE("nerve pain medicine", "नसों के दर्द की दवा", "nerve"),
    URINE("urine medicine", "पेशाब की दवा", "urine"),
}

/**
 * The purpose of a medicine, suggested from its ingredients and confirmed by the family.
 *
 * The phone never decides this alone. A wrong "this is your sugar medicine" is worse than no purpose at all,
 * so the suggestion comes from the table below, which holds only ingredients with one common use, and it is
 * shown to the family, who say yes or pick another. Only a confirmed purpose is ever spoken.
 * An ingredient that is not in the table can be put to the on-phone model, which may answer only with one of
 * the kinds above or "unknown" (see [PurposePrompt]); that too is only a suggestion for the family.
 */
object Purposes {
    /** Stored on the medicine when the family said the suggestion is wrong and chose nothing instead. */
    const val NONE = "NONE"

    fun kind(key: String): PurposeKind? = PurposeKind.entries.firstOrNull { it.name == key }

    /** The confirmed purpose of this medicine, if the family gave one. */
    fun of(med: Medicine): PurposeKind? = kind(med.purpose)

    /** True when the family has not been asked yet and the table has something to offer. */
    fun unasked(med: Medicine): Boolean = med.purpose.isEmpty() && suggest(med) != null

    /** What the ingredients say. Null when any ingredient is unknown here, or they point to no one purpose. */
    fun suggest(med: Medicine): PurposeKind? {
        val names = med.generics.map { TextNorm.normalise(it) }.filter { it.isNotBlank() }
        if (names.isEmpty()) return null
        val kinds = names.map { name -> kindOf(name, med) }
        // Paracetamol with a cold ingredient is a cold tablet, whatever else is in it.
        if (PurposeKind.FEVER_PAIN in kinds && names.any { name -> COLD_PARTNERS.any { word(name, it) } }) return PurposeKind.COLD
        val known = kinds.filterNotNull()
        // Every ingredient must be known. One unknown ingredient may be the one that matters.
        if (known.size != kinds.size) return null
        val distinct = known.distinct()
        return when {
            distinct.size == 1 -> distinct.single()
            // A helper beside the main ingredient: vitamins and calcium together, an antacid beside a painkiller.
            distinct.toSet() == setOf(PurposeKind.CALCIUM, PurposeKind.VITAMIN) -> PurposeKind.CALCIUM
            distinct.toSet() == setOf(PurposeKind.PAIN, PurposeKind.FEVER_PAIN) -> PurposeKind.PAIN
            else -> null
        }
    }

    private fun kindOf(name: String, med: Medicine): PurposeKind? {
        // Low-dose aspirin is taken for the heart; a full tablet is a painkiller. Without a strength it is not guessed.
        if (word(name, "ASPIRIN") || word(name, "ACETYLSALICYLIC")) {
            val mg = med.strengths.filter { it.unit == StrengthUnit.MG }.mapNotNull { it.value.toDoubleOrNull() }.minOrNull() ?: return null
            return if (mg <= 162.5) PurposeKind.BLOOD_THINNER else PurposeKind.PAIN
        }
        return TABLE.firstOrNull { (words, _) -> words.any { word(name, it) } }?.second
    }

    private fun word(name: String, wanted: String): Boolean = name.split(' ').any { it == wanted }

    private val COLD_PARTNERS = listOf("PHENYLEPHRINE", "CHLORPHENIRAMINE", "CHLORPHENAMINE", "PSEUDOEPHEDRINE", "DEXTROMETHORPHAN", "CETIRIZINE", "LEVOCETIRIZINE")

    private fun row(kind: PurposeKind, vararg words: String) = words.toList() to kind

    /** Ingredients common on Indian strips that have one everyday use. Anything with several common uses is left out. */
    private val TABLE: List<Pair<List<String>, PurposeKind>> = listOf(
        row(PurposeKind.FEVER_PAIN, "PARACETAMOL", "ACETAMINOPHEN"),
        row(PurposeKind.PAIN, "IBUPROFEN", "DICLOFENAC", "ACECLOFENAC", "NAPROXEN", "ETORICOXIB", "TRAMADOL", "NIMESULIDE", "KETOROLAC", "PIROXICAM"),
        row(PurposeKind.BP, "TELMISARTAN", "LOSARTAN", "OLMESARTAN", "VALSARTAN", "AMLODIPINE", "AZELNIDIPINE", "CILNIDIPINE", "RAMIPRIL", "ENALAPRIL",
            "LISINOPRIL", "PERINDOPRIL", "ATENOLOL", "NEBIVOLOL", "HYDROCHLOROTHIAZIDE", "CHLORTHALIDONE", "INDAPAMIDE", "PRAZOSIN"),
        row(PurposeKind.HEART, "METOPROLOL", "BISOPROLOL", "CARVEDILOL", "ISOSORBIDE", "NITROGLYCERIN", "DIGOXIN", "IVABRADINE", "RANOLAZINE"),
        row(PurposeKind.SUGAR, "METFORMIN", "GLIMEPIRIDE", "GLICLAZIDE", "GLIBENCLAMIDE", "GLIPIZIDE", "SITAGLIPTIN", "VILDAGLIPTIN", "TENELIGLIPTIN",
            "LINAGLIPTIN", "DAPAGLIFLOZIN", "EMPAGLIFLOZIN", "PIOGLITAZONE", "VOGLIBOSE", "ACARBOSE"),
        row(PurposeKind.CHOLESTEROL, "ATORVASTATIN", "ROSUVASTATIN", "SIMVASTATIN", "PITAVASTATIN", "FENOFIBRATE", "EZETIMIBE"),
        row(PurposeKind.BLOOD_THINNER, "CLOPIDOGREL", "PRASUGREL", "TICAGRELOR", "WARFARIN", "ACENOCOUMAROL", "APIXABAN", "RIVAROXABAN", "DABIGATRAN"),
        row(PurposeKind.THYROID, "LEVOTHYROXINE", "THYROXINE", "CARBIMAZOLE"),
        row(PurposeKind.ACIDITY, "PANTOPRAZOLE", "OMEPRAZOLE", "RABEPRAZOLE", "ESOMEPRAZOLE", "LANSOPRAZOLE", "RANITIDINE", "FAMOTIDINE"),
        row(PurposeKind.NAUSEA, "ONDANSETRON", "DOMPERIDONE"),
        row(PurposeKind.ALLERGY, "CETIRIZINE", "LEVOCETIRIZINE", "FEXOFENADINE", "LORATADINE", "DESLORATADINE", "CHLORPHENIRAMINE", "CHLORPHENAMINE", "BILASTINE"),
        row(PurposeKind.BREATHING, "SALBUTAMOL", "LEVOSALBUTAMOL", "THEOPHYLLINE", "DOXOFYLLINE", "MONTELUKAST", "ACEBROPHYLLINE"),
        row(PurposeKind.ANTIBIOTIC, "AMOXICILLIN", "AMOXYCILLIN", "AZITHROMYCIN", "CEFIXIME", "CEFPODOXIME", "CEFUROXIME", "CIPROFLOXACIN", "OFLOXACIN",
            "LEVOFLOXACIN", "DOXYCYCLINE", "CLAVULANATE", "CLAVULANIC", "NITROFURANTOIN", "CLARITHROMYCIN"),
        row(PurposeKind.CALCIUM, "CALCIUM", "CALCITRIOL"),
        row(PurposeKind.VITAMIN, "CHOLECALCIFEROL", "VITAMIN", "METHYLCOBALAMIN", "MECOBALAMIN", "CYANOCOBALAMIN", "FOLIC", "PYRIDOXINE", "THIAMINE",
            "BENFOTIAMINE", "RIBOFLAVIN", "NIACINAMIDE", "BIOTIN", "ZINC"),
        row(PurposeKind.IRON, "FERROUS", "FERRIC", "IRON"),
        row(PurposeKind.NERVE, "GABAPENTIN", "PREGABALIN"),
        row(PurposeKind.URINE, "TAMSULOSIN", "SILODOSIN", "DUTASTERIDE", "FINASTERIDE"),
    )
}

/**
 * The question put to the on-phone model for an ingredient the table does not hold. The model may answer only
 * with one kind from the fixed list or UNKNOWN: its output is checked against that list and anything else is
 * dropped. Its answer is shown to the family as a suggestion and is never spoken until they confirm it.
 */
object PurposePrompt {
    val SYSTEM = "You sort one medicine into one everyday group by its active ingredients. You never give medical advice. " +
        "Choose exactly one group from this list, or UNKNOWN when you are not sure or it fits none: " +
        PurposeKind.entries.joinToString(", ") { "${it.name} (${it.en})" } + ". Output only the JSON object."

    val SCHEMA = """{"type": "object", "properties": {"group": {"type": "string", "enum": [""" +
        (PurposeKind.entries.map { it.name } + "UNKNOWN").joinToString(", ") { "\"$it\"" } + """]}}, "required": ["group"]}"""

    fun question(med: Medicine): String = "Brand: ${med.brand}. Active ingredients: ${med.generics.joinToString(", ")}. " +
        "Strength: ${Strengths.display(med.strengths)}."

    /** The kind the reply names. Anything that is not exactly one of the kinds gives nothing. */
    fun parse(reply: String): PurposeKind? {
        val named = Regex("\"group\"\\s*:\\s*\"([A-Z_]+)\"").find(reply)?.groupValues?.get(1) ?: return null
        return Purposes.kind(named)
    }
}
