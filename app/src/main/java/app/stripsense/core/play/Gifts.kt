package app.stripsense.core.play

import java.time.LocalDate
import kotlin.random.Random

enum class GiftKind(val en: String, val hi: String) {
    THOUGHT("A good thought", "एक अच्छा विचार"), MOVIE("A movie", "एक फ़िल्म"), BOOK("A book", "एक किताब"),
    ANIME("An anime", "एक एनिमे"), KDRAMA("A K-drama", "एक के-ड्रामा");

    fun name(lang: String) = if (lang == "hi") hi else en
}

/** One gift: a thing worth an evening. [by] is who made it, empty for a thought. [whyEn], [whyHi]: one line on why. */
data class Gift(val id: String, val kind: GiftKind, val title: String, val by: String, val whyEn: String, val whyHi: String) {
    fun why(lang: String) = if (lang == "hi") whyHi else whyEn
}

/** A gift that was given, and the day it was given on. Kept as "day|id". */
data class Given(val day: String, val gift: Gift)

/**
 * The day's gift: when the day's medicine is on track, the person chooses a kind and is given one thing of that
 * kind. The phone remembers every gift it has given and does not give the same one twice until it has given them
 * all. The list is inside the app, so it needs no network.
 */
object Gifts {
    val ALL: List<Gift> = listOf(
        Gift("t1", GiftKind.THOUGHT, "A small thing done every day is bigger than a big thing done once.", "", "You did the small thing today.", "आज आपने वह छोटा काम कर लिया।"),
        Gift("t2", GiftKind.THOUGHT, "You kept a promise to yourself today.", "", "That is the hardest kind to keep.", "ख़ुद से किया वादा निभाना सबसे कठिन होता है।"),
        Gift("t3", GiftKind.THOUGHT, "The day is lighter when the important thing is already done.", "", "The rest of today is yours.", "बाकी दिन अब आपका है।"),
        Gift("t4", GiftKind.THOUGHT, "Somebody is glad you are looking after yourself.", "", "Tell them you did.", "उन्हें बताइए कि आपने ध्यान रखा।"),
        Gift("t5", GiftKind.THOUGHT, "A habit is a kindness you do for the person you will be tomorrow.", "", "Tomorrow's you says thank you.", "कल के आप आज के आपको धन्यवाद कहेंगे।"),
        Gift("t6", GiftKind.THOUGHT, "Nobody builds a wall in a day. They lay one brick, well.", "", "Today's brick is laid.", "आज की ईंट रख दी गई।"),
        Gift("t7", GiftKind.THOUGHT, "Slow and steady is still moving.", "", "And you moved today.", "और आज आप आगे बढ़े।"),
        Gift("t8", GiftKind.THOUGHT, "Being on time for yourself is a kind of respect.", "", "You showed it today.", "आज आपने वह सम्मान दिखाया।"),

        Gift("m1", GiftKind.MOVIE, "Piku", "Shoojit Sircar, 2015", "A daughter, her ageing father and a road trip.", "एक बेटी, उसके बूढ़े पिता और एक लंबा सफ़र।"),
        Gift("m2", GiftKind.MOVIE, "Taare Zameen Par", "Aamir Khan, 2007", "A teacher sees what a child can really do.", "एक शिक्षक देखता है कि बच्चा असल में क्या कर सकता है।"),
        Gift("m3", GiftKind.MOVIE, "Up", "Pixar, 2009", "An old man ties balloons to his house and flies off on an adventure.", "एक बुज़ुर्ग अपने घर में ग़ुब्बारे बाँधकर सफ़र पर उड़ जाता है।"),
        Gift("m4", GiftKind.MOVIE, "English Vinglish", "Gauri Shinde, 2012", "A mother quietly learns English, and her own worth.", "एक माँ चुपचाप अंग्रेज़ी सीखती है, और अपनी क़ीमत भी।"),
        Gift("m5", GiftKind.MOVIE, "Coco", "Pixar, 2017", "A boy, music, and remembering the family that came before.", "एक लड़का, संगीत, और अपने पुरखों की याद।"),
        Gift("m6", GiftKind.MOVIE, "3 Idiots", "Rajkumar Hirani, 2009", "Three friends in an engineering college, and what learning is for.", "इंजीनियरिंग कॉलेज के तीन दोस्त, और पढ़ाई का असली मतलब।"),

        Gift("b1", GiftKind.BOOK, "Atomic Habits", "James Clear", "Small habits, repeated, change everything. You are doing it already.", "छोटी आदतें दोहराने से सब बदल जाता है। आप यह कर ही रहे हैं।"),
        Gift("b2", GiftKind.BOOK, "Wings of Fire", "A. P. J. Abdul Kalam", "From a small town in Rameswaram to India's rockets, in his own words.", "रामेश्वरम के छोटे क़स्बे से भारत के रॉकेट तक, उन्हीं के शब्दों में।"),
        Gift("b3", GiftKind.BOOK, "Malgudi Days", "R. K. Narayan", "Short stories of one small Indian town. One a day is enough.", "एक छोटे भारतीय क़स्बे की कहानियाँ। दिन में एक काफ़ी है।"),
        Gift("b4", GiftKind.BOOK, "Ikigai", "Héctor García and Francesc Miralles", "What gets people in Okinawa up in the morning for a hundred years.", "ओकिनावा के लोग सौ साल तक हर सुबह किस वजह से उठते हैं।"),
        Gift("b5", GiftKind.BOOK, "The Little Prince", "Antoine de Saint-Exupéry", "A small book that grown-ups need more than children do.", "एक छोटी किताब, जिसकी ज़रूरत बच्चों से ज़्यादा बड़ों को है।"),
        Gift("b6", GiftKind.BOOK, "The Room on the Roof", "Ruskin Bond", "A boy in Dehradun finds friends and freedom. Bond wrote it at seventeen.", "देहरादून का एक लड़का दोस्त और आज़ादी पाता है। बॉन्ड ने इसे सत्रह की उम्र में लिखा।"),

        Gift("a1", GiftKind.ANIME, "My Neighbor Totoro", "Studio Ghibli, 1988", "Two sisters, a forest spirit, and a mother getting better in hospital.", "दो बहनें, जंगल की एक आत्मा, और अस्पताल में ठीक होती माँ।"),
        Gift("a2", GiftKind.ANIME, "Spirited Away", "Studio Ghibli, 2001", "A girl works in a bathhouse of spirits to save her parents.", "एक लड़की अपने माता-पिता को बचाने के लिए आत्माओं के स्नानघर में काम करती है।"),
        Gift("a3", GiftKind.ANIME, "Haikyu!!", "Series", "A short boy and a volleyball team that will not give up.", "एक छोटे क़द का लड़का और हार न मानने वाली वॉलीबॉल टीम।"),
        Gift("a4", GiftKind.ANIME, "Kiki's Delivery Service", "Studio Ghibli, 1989", "A young witch starts a delivery service in a new town.", "एक छोटी जादूगरनी नए शहर में सामान पहुँचाने का काम शुरू करती है।"),
        Gift("a5", GiftKind.ANIME, "Barakamon", "Series", "A calligrapher from the city learns from the children of a small island.", "शहर का एक सुलेखक छोटे द्वीप के बच्चों से सीखता है।"),
        Gift("a6", GiftKind.ANIME, "Spy x Family", "Series", "A spy, an assassin and a mind-reading child pretend to be a family.", "एक जासूस, एक हत्यारिन और मन पढ़ने वाली बच्ची परिवार होने का नाटक करते हैं।"),

        Gift("k1", GiftKind.KDRAMA, "Hospital Playlist", "Series", "Five doctors who have been friends for twenty years, and their band.", "बीस साल से दोस्त पाँच डॉक्टर, और उनका बैंड।"),
        Gift("k2", GiftKind.KDRAMA, "Reply 1988", "Series", "Five families on one lane in Seoul, in 1988.", "1988 में सियोल की एक गली के पाँच परिवार।"),
        Gift("k3", GiftKind.KDRAMA, "Navillera", "Series", "A seventy-year-old man finally learns ballet.", "सत्तर साल का एक आदमी आख़िरकार बैले सीखता है।"),
        Gift("k4", GiftKind.KDRAMA, "Extraordinary Attorney Woo", "Series", "A brilliant young lawyer who is autistic, and the cases she wins.", "एक होनहार युवा वकील, जो ऑटिस्टिक है, और उसके जीते हुए मुक़दमे।"),
        Gift("k5", GiftKind.KDRAMA, "Hometown Cha-Cha-Cha", "Series", "A dentist from Seoul moves to a seaside village.", "सियोल की एक दंत चिकित्सक समुद्र किनारे के गाँव में आ बसती है।"),
        Gift("k6", GiftKind.KDRAMA, "Dear My Friends", "Series", "Old friends in their seventies, and the lives they are still living.", "सत्तर की उम्र के पुराने दोस्त, और उनकी अब भी चलती ज़िंदगी।"),
    )

    private val BY_ID = ALL.associateBy { it.id }

    /** What was given, oldest first, from what the phone kept. An entry it no longer knows is left out. */
    fun given(kept: List<String>): List<Given> = kept.mapNotNull { entry ->
        val day = entry.substringBefore('|')
        BY_ID[entry.substringAfter('|')]?.let { Given(day, it) }
    }

    fun givenOn(kept: List<String>, day: LocalDate): Given? = given(kept).lastOrNull { it.day == day.toString() }

    /**
     * The gift of [kind] for [day]: one the person has not been given yet. When every one of that kind has been
     * given, the one given longest ago comes round again. Which of the new ones it is depends on the day.
     */
    fun pick(kind: GiftKind, kept: List<String>, day: LocalDate): Gift {
        val ofKind = ALL.filter { it.kind == kind }
        val had = given(kept).map { it.gift.id }
        val fresh = ofKind.filter { it.id !in had }
        if (fresh.isNotEmpty()) return fresh.random(Random(day.toEpochDay() * 31 + kind.ordinal))
        return ofKind.minByOrNull { had.lastIndexOf(it.id) } ?: ofKind.first()
    }

    fun entry(day: LocalDate, gift: Gift): String = "$day|${gift.id}"
}
