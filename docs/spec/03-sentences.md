# Spec 03. Every sentence the phone says

All answer text lives in `core/Sentences.kt`. Each sentence has an ID, an English form and a Hindi form. A `Part` is an ID plus its values. `Sentences.render(parts, lang, person)` returns, for each part, the text to **show** and the text to **speak**. The two differ only in how numbers and times are written.

Rules:
- No answer text exists outside this table. Button labels are in section 5.
- The word is always "marked". No sentence says a dose was taken.
- Only a strip answer after verdict Match may use NAME, P1, P3, A1 or W1.
- When no person name is set, a sentence that starts with `{Call}, ` starts without it, with a capital letter. A `{Call}` anywhere else is dropped together with the comma (if there is one) and the spaces in front of it: "Hi. What would you like to know?", "You're welcome.", "नमस्ते, बताइए, क्या जानना है?", "कोई बात नहीं।".

## 0. The types
`Person` goes in `core/Person.kt`. The others go in `core/Sentences.kt`. `render` is a function of `object Sentences`.
```kotlin
@Serializable
data class Person(
    val callEn: String = "Maa", val callHi: String = "माँ", val isWoman: Boolean = true,
    val contactEn: String = "Rahul", val contactHi: String = "राहुल",
    val contactPhone: String = "", val pin: String = "1234",
) {
    fun call(lang: String) = if (lang == "hi") callHi else callEn
    fun contact(lang: String) = if (lang == "hi") contactHi else contactEn
}
enum class Slot { MORNING, AFTERNOON, EVENING, NIGHT;
    companion object { fun of(time: LocalTime): Slot = /* by the hour, see section 1 */ }
}
data class Bilingual(val en: String, val hi: String)             // a nickname or a note
data class ListItem(val nickname: Bilingual, val time: LocalTime)
data class WhenValue(val daysAgo: Int, val date: LocalDate)      // daysAgo = difference in dosing dates
data class Part(val id: String, val values: Map<String, Any> = emptyMap())
data class Rendered(val shown: String, val spoken: String, val boxed: String? = null)   // boxed = the {printed} text of V1
fun render(parts: List<Part>, lang: String, person: Person): List<Rendered>              // lang is "en" or "hi"
```
| Key in `Part.values` | Type | Fills |
|---|---|---|
| `nickname`, `note` | `Bilingual` | `{nickname}`, `{note}` |
| `strength` | `List<Strength>` | `{strength}` |
| `time` | `LocalTime` | `{time}` and `{at_time}`. `{at_time}` is never passed; it is made from `time` |
| `slot` | `Slot` | `{slot}`, `{now_part}` and `{this_slot}`, all three from the one value |
| `ago` | `Int` (minutes), or left out | `{ago}` |
| `when` | `WhenValue` | `{when}` |
| `date` | `LocalDate` | `{date}` |
| `printed`, `listpart` | `String` | `{printed}`, `{listpart}` |
| `list` | `List<ListItem>` | `{list}` |

`{Call}`, `{contact}` and `{family}` come from `person`, never from the values. A sentence with a second form for a woman uses it when `person.isWoman`.

## 1. Values
| Value | Shown | Spoken (English) | Spoken (Hindi) |
|---|---|---|---|
| `{Call}` | the person's call name: Maa / माँ | same | same |
| `{contact}`, `{family}` | the family contact's name: Rahul / राहुल | same | same |
| `{nickname}` | fever tablet / बुख़ार की गोली | same | same |
| `{strength}` | `500 mg`; several joined with " + " | five hundred milligrams; joined with " and " | पाँच सौ मिलीग्राम; joined with " और " |
| `{time}` | English `8:05 am`. Hindi `सुबह 8:05`, or `सुबह 8 बजे` on the hour | eight oh five in the morning; eight o'clock at night | सुबह आठ बजकर पाँच मिनट; सुबह आठ बजे |
| `{at_time}` (Hindi sentences; made from `time`) | `सुबह 8:05 पर`, or `सुबह 8 बजे` on the hour | (English uses `{time}`) | सुबह आठ बजकर पाँच मिनट पर; सुबह आठ बजे |
| `{slot}` | morning / afternoon / evening / night · सुबह / दोपहर / शाम / रात | same | same |
| `{now_part}` | this morning / this afternoon / this evening / tonight · आज सुबह / आज दोपहर / आज शाम / आज रात | same | same |
| `{this_slot}` | this morning's dose / this afternoon's dose / this evening's dose / tonight's dose · सुबह की खुराक / दोपहर की खुराक / शाम की खुराक / रात की खुराक | same | same |
| `{when}` | today / yesterday / on Monday / on 4 October · आज / कल / सोमवार को / 4 अक्टूबर को | same as shown | same as shown |
| `{ago}` | only for marks under 24 hours old: `, 1 minute ago`, `, 12 minutes ago`, `, 1 hour ago`, `, 11 hours ago` · `, 12 मिनट पहले`, `, 11 घंटे पहले` | number in words | number in words |
| `{date}` | 1 October / 1 अक्टूबर | same as shown (the voice reads the day number itself) | same as shown |
| `{note}` | the family's note in that language | same | same |
| `{printed}`, `{listpart}` | strings from the Decider: `650 mg`, `plain 500 mg` | units spoken as words | units spoken as words |
| `{list}` | English: `your fever tablet at 2:00 pm`, joined with ", " and a final " and ". Hindi: `बुख़ार की गोली दोपहर 2 बजे`, joined with ", " and a final " और " | times in words | times in words |

Parts of the day, by the hour of the time: 04:00 to 11:59 morning, 12:00 to 15:59 afternoon, 16:00 to 19:59 evening, 20:00 to 03:59 night. English spoken forms: "in the morning", "in the afternoon", "in the evening", "at night". The hour is spoken as 1 to 12. Minutes from 10 to 59 are a plain number: 8:15 am is "eight fifteen in the morning". 12:00 noon is "twelve o'clock in the afternoon". 12:30 am is "twelve thirty at night".

`{ago}`: minutes = whole minutes between the mark and now, at least 1. Under 60 → minutes. 60 or more → whole hours, rounded down (715 minutes is "11 hours ago"). 1,440 or more → empty. English says "minute" and "hour" for 1, "minutes" and "hours" for more. Hindi uses the same word for both.

`{when}`: compare dosing dates. Same → today. One day → yesterday. Up to 6 days → "on " + weekday. More → "on " + date.

Units when spoken: mg milligrams / मिलीग्राम · mcg micrograms / माइक्रोग्राम · g grams / ग्राम · IU international units / आई-यू · ml millilitres / एम-एल · % per cent / प्रतिशत. A decimal is spoken digit by digit after "point" / "दशमलव": 2.5 → "two point five" / "दो दशमलव पाँच".

English numbers in words: the usual forms; 650 → "six hundred and fifty"; 1250 → "one thousand two hundred and fifty"; minutes under 10 → "oh five".

Hindi numbers 0 to 99, in order (the first is 0, the last is 99):

शून्य · एक · दो · तीन · चार · पाँच · छह · सात · आठ · नौ · दस · ग्यारह · बारह · तेरह · चौदह · पंद्रह · सोलह · सत्रह · अठारह · उन्नीस · बीस · इक्कीस · बाईस · तेईस · चौबीस · पच्चीस · छब्बीस · सत्ताईस · अट्ठाईस · उनतीस · तीस · इकतीस · बत्तीस · तैंतीस · चौंतीस · पैंतीस · छत्तीस · सैंतीस · अड़तीस · उनतालीस · चालीस · इकतालीस · बयालीस · तैंतालीस · चवालीस · पैंतालीस · छियालीस · सैंतालीस · अड़तालीस · उनचास · पचास · इक्यावन · बावन · तिरपन · चौवन · पचपन · छप्पन · सत्तावन · अट्ठावन · उनसठ · साठ · इकसठ · बासठ · तिरसठ · चौंसठ · पैंसठ · छियासठ · सड़सठ · अड़सठ · उनहत्तर · सत्तर · इकहत्तर · बहत्तर · तिहत्तर · चौहत्तर · पचहत्तर · छिहत्तर · सतहत्तर · अठहत्तर · उन्यासी · अस्सी · इक्यासी · बयासी · तिरासी · चौरासी · पचासी · छियासी · सत्तासी · अट्ठासी · नवासी · नब्बे · इक्यानबे · बानबे · तिरानबे · चौरानबे · पंचानबे · छियानबे · सत्तानबे · अट्ठानबे · निन्यानबे

100 = सौ (`पाँच सौ`, `छह सौ पचास`); 1000 = हज़ार. Months: जनवरी, फ़रवरी, मार्च, अप्रैल, मई, जून, जुलाई, अगस्त, सितंबर, अक्टूबर, नवंबर, दिसंबर. Weekdays from Sunday: रविवार, सोमवार, मंगलवार, बुधवार, गुरुवार, शुक्रवार, शनिवार.

## 2. Strip answers and the question
| ID | English | Hindi |
|---|---|---|
| NAME | That's your {nickname}, {strength}. | ये आपकी {nickname} है, {strength}। |
| P1 | {Call}, this is your {nickname}, {strength}, the {slot} one. Nothing's marked for {now_part} yet. | {Call}, ये आपकी {nickname} है, {strength}, {slot} वाली। {now_part} की खुराक अभी दर्ज नहीं हुई है। |
| P3 | That's your {nickname}, {strength}. Nothing's marked for {now_part}. | ये आपकी {nickname} है, {strength}। {now_part} की खुराक दर्ज नहीं हुई है। |
| A1 | That's your {nickname}, {strength}. It's already marked for {now_part}, at {time}{ago}. | ये आपकी {nickname} है, {strength}। {now_part} की खुराक {at_time} दर्ज हो चुकी है{ago}। |
| W1 | That's your {nickname}, {strength}. It isn't time yet: the next one is the {slot} one, at {time}. | ये आपकी {nickname} है, {strength}। अभी इसका समय नहीं हुआ, अगली खुराक {slot} की है, {at_time}। |
| P2 | Last one was marked {when} at {time}{ago}. | पिछली खुराक {when} {at_time} दर्ज हुई थी{ago}। |
| P2n | It hasn't been marked before. | ये पहले कभी दर्ज नहीं हुई है। |
| NX1 | The next one is at {time}. | अगली खुराक {at_time} है। |
| NS1 | Earlier, at {time}, you said you weren't sure about {this_slot}. Please check with {contact} before taking another one. | {at_time} आपने कहा था कि {this_slot} के बारे में पक्का नहीं पता। दूसरी लेने से पहले {contact} से पूछ लीजिए। |
| CHK | Not sure? Ask {contact} before taking another. | पक्का न हो, तो दूसरी लेने से पहले {contact} से पूछ लीजिए। |
| NOTE | {family}'s note: {note}. | {family} ने लिखा है: {note}। |
| Q1 | Are you taking it now? | अभी ले रहे हैं? / (to a woman) अभी ले रही हैं? |
| R1 | Okay, I've marked your {nickname} at {time}. | ठीक है, आपकी {nickname} {at_time} दर्ज कर दी। |
| U2 | Okay, I've taken the mark off. It's not marked now. | ठीक है, निशान हटा दिया। अब ये दर्ज नहीं है। |
| NO1 | Okay, I won't mark it. | ठीक है, इसे दर्ज नहीं किया। |
| NS3 | Okay, I've noted that you're not sure. Best to check with {contact}. | ठीक है, लिख लिया कि पक्का नहीं पता। एक बार {contact} से पूछ लीजिए। |
| NM1 | Okay, it's not marked. | ठीक है, दर्ज नहीं हुई। |
| AM1 | That's already marked, at {time}{ago}. | ये पहले से {at_time} दर्ज है{ago}। |
| MB1 | Shall I mark your {nickname} for the {slot}? | आपकी {nickname}, {slot} वाली, दर्ज कर दूँ? |
| MB0 | It isn't time for it right now. | अभी इसका समय नहीं है। |

Q1 has two Hindi forms; use the second when the person is set as a woman (the demo person is).

## 3. Refusals, voice answers, help
| ID | English | Hindi |
|---|---|---|
| RZ1 | Just one strip at a time, please. | एक बार में एक ही पट्टी दिखाइए। |
| C1 | I can't read this one. Could you turn it over? | ये पढ़ी नहीं जा रही। पट्टी पलट कर दिखाइए। |
| C2 | Still can't read it. Try laying it flat under a light. | अब भी नहीं पढ़ी जा रही। पट्टी को रोशनी में सीधा रखिए। |
| C3 | I can't be sure about this one. Please ask {contact} or your chemist. | इसके बारे में पक्का नहीं कह सकते। {contact} या केमिस्ट से पूछ लीजिए। |
| L1 | I can't check this one with the camera. Please use its Mark button. | इसे कैमरे से नहीं जाँच सकते। इसके बटन से दर्ज कीजिए। |
| L2 | This one hasn't been taught yet. Please ask {contact} to teach it. | ये अभी सिखाई नहीं गई है। {contact} से सिखाने को कहिए। |
| S1 | This one is marked as stopped on your list, since {date}. | आपकी सूची में ये दवा {date} से बंद है। |
| S0 | Please ask {contact} before you take it. | लेने से पहले {contact} से पूछ लीजिए। |
| N1 | This looks like your {nickname}, {strength}, but it's a strip I haven't seen before. If it's the box, show me the strip inside. If it's a new strip, ask {contact} to add it. | ये आपकी {nickname}, {strength} जैसी लग रही है, पर ये पट्टी पहले कभी नहीं दिखाई गई। डिब्बा है तो अंदर की पट्टी दिखाइए। नई पट्टी है तो {contact} से इसे जुड़वा लीजिए। |
| D1 | This isn't the medicine on your list. Please check with {contact}. | ये आपकी सूची वाली दवा नहीं है। {contact} से पूछ लीजिए। |
| V1 | Careful: this strip says {printed}, but yours is {listpart}. This one isn't on your list. | ध्यान दीजिए: इस पट्टी पर {printed} लिखा है, आपकी वाली {listpart} है। ये आपकी सूची में नहीं है। |
| U1 | I'm not sure about this one. Please check with {contact}. | इसके बारे में पक्का नहीं कह सकते। {contact} से पूछ लीजिए। |
| K1 | I don't recognise this one. It isn't on anyone's list here. | ये दवा पहचान में नहीं आई। ये यहाँ किसी की सूची में नहीं है। |

| ID | English | Hindi |
|---|---|---|
| VS1 | Your {nickname}: nothing's marked for {now_part} yet. | आपकी {nickname}: {now_part} की खुराक अभी दर्ज नहीं हुई है। |
| VS2 | Your {nickname} is marked for {now_part}, at {time}{ago}. | आपकी {nickname}: {now_part} की खुराक {at_time} दर्ज है{ago}। |
| VS3 | Your {nickname}: at {time} you said you weren't sure about {this_slot}. | आपकी {nickname}: {at_time} आपने कहा था कि {this_slot} के बारे में पक्का नहीं पता। |
| VS4 | Your {nickname}: nothing's marked for {now_part}. | आपकी {nickname}: {now_part} की खुराक दर्ज नहीं हुई है। |
| VS6 | Your {nickname} isn't due yet. The next one is the {slot} one, at {time}. | आपकी {nickname} का अभी समय नहीं हुआ। अगली खुराक {slot} की है, {at_time}। |
| VS8 | Your {nickname} is marked as stopped on your list, since {date}. | आपकी {nickname} आपकी सूची में {date} से बंद है। |
| V0 | Sorry, I didn't catch that. Could you say it again? | माफ़ कीजिए, ठीक से समझ नहीं आया। एक बार फिर बोलिए? |
| V2 | Hold up the strip and I'll check it for you. | पट्टी दिखाइए, देख कर बताते हैं। |
| V4 | Voice is getting ready. The buttons work. | बोलकर पूछना अभी तैयार हो रहा है। बटन काम कर रहे हैं। |
| V5 | Please hold up the strip. | पट्टी दिखाइए। |
| V6 | Hold up your strips one at a time and I'll tell you which is which. | एक-एक करके पट्टियाँ दिखाइए, हर एक के बारे में बता देंगे। |
| V9 | That one has its own Mark button on the Today screen. | उसका अपना बटन "आज" वाली स्क्रीन पर है। |
| WL0 | Everything on today's list is marked. | आज की सूची की हर खुराक दर्ज है। |
| WL0b | There's nothing on your list for today. | आज के लिए आपकी सूची में कुछ नहीं है। |
| WL1 | Later today: {list}. | आज आगे: {list}। |
| WL2 | Not marked yet: {list}. | अभी दर्ज नहीं हुईं: {list}। |
| WL2x | Not marked, and the time has passed: {list}. | दर्ज नहीं हुईं, और समय निकल गया: {list}। |
| WL3 | You weren't sure about: {list}. | इनके बारे में पक्का नहीं था: {list}। |
| MQ1 | That's one for your doctor or chemist. I don't want to guess. | ये डॉक्टर या केमिस्ट से पूछने वाली बात है। इसमें अंदाज़ा लगाना ठीक नहीं। |
| MD1 | I can't advise on that. Please check with {contact} before taking it late. | इस पर सलाह नहीं दे सकते। देर से लेने से पहले {contact} से पूछ लीजिए। |
| CL1 | Shall I call {contact}? | {contact} को फ़ोन लगा दूँ? |
| EM1 | Press the green button to call 112. | एक-एक-दो पर फ़ोन करने के लिए हरा बटन दबाइए। |
| SH1 | You don't have to go through this alone. You can talk to someone right now: Tele-MANAS, 14416. It's free, any time. | आपको ये अकेले नहीं सहना है। आप अभी किसी से बात कर सकते हैं: टेली-मानस, एक चार चार एक छह। मुफ़्त है, किसी भी समय। / (to a woman) आपको ये अकेले नहीं सहना है। आप अभी किसी से बात कर सकती हैं: टेली-मानस, एक चार चार एक छह। मुफ़्त है, किसी भी समय। |
| HLP1 | I've opened the Help screen. Tap a button to call someone. | मदद वाली स्क्रीन खोल दी है। किसी को फ़ोन करने के लिए बटन दबाइए। |
| HELLO | Hi {Call}. What would you like to know? | नमस्ते {Call}, बताइए, क्या जानना है? |
| THX | You're welcome, {Call}. | कोई बात नहीं, {Call}। |
| RP0 | There's nothing to repeat yet. | दोहराने के लिए अभी कुछ नहीं है। |

SH1 has two Hindi forms; use the second when the person is set as a woman.

## 4. Which sentence for which verdict (spec 01)
| Verdict | Parts | Screen title |
|---|---|---|
| Match | from `DoseEngine.stripAnswer` | by the row: "Due now · not marked yet", "Already marked", or the nickname alone |
| Different | V1 | Not on your list |
| WrongMedicine | D1 | Not on your list |
| NotShown | N1 | Not a strip I was shown |
| Stopped | S1, S0 | Stopped on your list |
| ButtonOnly | L1 | the nickname |
| NotTaught | L2 | the nickname |
| Unknown | K1 | Not recognised |
| OneAtATime | RZ1 | One strip at a time |
| Unreadable | C1 the first time; C2 the second time in a row; C3 the third | Can't read the print |
| NotSure | U1 | Can't read the print |

The "Screen title" column is the title in the verdict banner. The top bar of the Answer screen always says `resultTitle`.

The count of Unreadable verdicts is kept in `AppState`. It goes up by one with each Unreadable verdict. It goes back to zero with any other verdict, and when the person opens Today, Ask, Numbers or Family (those four are the tabs; "Scan another" keeps the count). From the third one on it stays at C3.

## 5. Labels on buttons and screens (not spoken)
These live in `ui/Labels.kt`. If a screen needs a label that is not here, add it there in both languages, short and plain, and list it in the stage report. In a label, `{c}` is a name, `{t}` a time written as in section 1, `{s}` the slot word (Morning / सुबह), `{n}` a number, `{x}` a strength.

Greetings by the clock: `greetMorning` from 04:00 to 11:59, `greetAfternoon` from 12:00 to 15:59, `greetEvening` at any other time.

| Key | English | Hindi |
|---|---|---|
| hold | Hold up a strip | पट्टी दिखाइए |
| talk | Ask by voice | बोलकर पूछिए |
| help | Help | मदद |
| today | Today | आज |
| yes | Yes | हाँ |
| no | No | नहीं |
| notsure | Not sure | पता नहीं |
| mark | Mark | दर्ज करें |
| undo | Undo | हटाएँ |
| again | Say it again | फिर से सुनिए |
| close | Close | बंद करें |
| back | Back | वापस |
| call | Call {c} | {c} को फ़ोन करें |
| call112 | Call 112 | 112 पर फ़ोन करें |
| callTele | Call 14416 | 14416 पर फ़ोन करें |
| reading | Reading the strip… | पट्टी पढ़ी जा रही है… |
| listening | Listening… | सुना जा रहा है… |
| marked | Marked | दर्ज |
| notMarked | Not marked | दर्ज नहीं |
| notSureState | Not sure | पक्का नहीं |
| dueNow | Due now | अभी का समय |
| later | Later | बाद में |
| stopped | Stopped | बंद |
| family | Family | परिवार |
| tryAgain | Try again | फिर से दिखाइए |
| guideHint | Hold the whole strip inside the frame | पूरी पट्टी चौकोर के अंदर रखिए |
| promptEnds | Buttons stay for 60 seconds | बटन 60 सेकंड तक रहेंगे |
| undoEnds | Undo is available for 60 seconds | 60 सेकंड तक हटा सकते हैं |
| done | Done | हो गया |
| pressGreen | Press the green button | यह हरा बटन दबाइए |
| eyeDropsNote | Checked by its button, not by camera | कैमरे से नहीं, इसके बटन से दर्ज होती है |
| scanAnother | Scan another | दूसरी पट्टी दिखाइए |
| resultTitle | Result | नतीजा |
| scan | Scan | स्कैन |
| scanStrip | Scan a strip | पट्टी स्कैन करें |
| hideText | Hide read text | पढ़ा हुआ छिपाएँ |
| greetMorning | Good morning, {c} | सुप्रभात, {c} |
| greetAfternoon | Good afternoon, {c} | नमस्ते, {c} |
| greetEvening | Good evening, {c} | शुभ संध्या, {c} |
| nextUp | Next up | अगली खुराक |
| doseLine | {s} dose · {t} | {s} की खुराक · {t} |
| markedAt | Marked at {t} | {t} पर दर्ज |
| confirm | Confirm | पक्का करें |
| cancel | Cancel | रहने दें |
| demoTitle | Demo controls | डेमो कंट्रोल |
| clockSet | Clock | घड़ी |
| realTime | Real time | असली समय |
| freshDay | Fresh day | नया दिन |
| seedYesterday | Seed yesterday | कल की खुराकें भरें |
| loadDemo | Load the demo list | डेमो सूची डालें |
| speakAnswers | Speak answers | जवाब बोलकर सुनाएँ |
| lookCheck | Look check | पट्टी की शक्ल की जाँच |
| secondReader | Second reader | दूसरी जाँच |
| tuningTitle | Tuning numbers | जाँच की संख्याएँ |
| resetAll | Reset everything | सब कुछ मिटाएँ |
| pinTitle | Enter the 4-digit PIN | 4 अंकों का PIN डालिए |
| pinWrong | That PIN isn't right | यह PIN सही नहीं है |
| tabList | List | सूची |
| tabTeach | Teach | सिखाएँ |
| tabFiles | Files | फ़ाइलें |
| tabSettings | Settings | सेटिंग |
| taught | Taught | सिखाई गई |
| notTaughtYet | Not taught yet | अभी सिखाई नहीं गई |
| importList | Import a list file | सूची की फ़ाइल डालिए |
| saveList | Save the list file | सूची की फ़ाइल सेव करें |
| savedInDownloads | Saved in Download/StripSense | Download/StripSense में सेव हो गई |
| badFile | This file cannot be used. Nothing was changed. | यह फ़ाइल इस्तेमाल नहीं हो सकती। कुछ नहीं बदला। |
| previewList | This list will replace the current one: | यह सूची मौजूदा सूची की जगह लेगी: |
| pickMed | Which medicine is this strip for? | यह पट्टी किस दवा की है? |
| saveStrip | Save this strip | यह पट्टी सेव करें |
| removeLooks | Remove looks | सिखाई हुई पट्टियाँ हटाएँ |
| cantTeach | This strip says {printed}, but the list has {listpart}, so it can't be taught for this medicine. | इस पट्टी पर {printed} लिखा है, पर सूची में {listpart} है, इसलिए यह इस दवा के लिए नहीं सिखाई जा सकती। |
| onTheList | On the list | सूची में |
| readNow | Read now | अभी पढ़ा |
| looksCount | {n} of 4 strips taught | 4 में से {n} पट्टियाँ सिखाई गईं |
| teachTime | Time taught: {t} | सिखाने में लगा समय: {t} |
| fullLooks | Four strips are saved. Remove looks to teach again. | चार पट्टियाँ सेव हैं। फिर से सिखाने के लिए पहले हटाएँ। |
| callNameEn | Call name (English) | पुकारने का नाम (अंग्रेज़ी) |
| callNameHi | Call name (Hindi) | पुकारने का नाम (हिंदी) |
| womanForms | Use the woman's forms | महिला वाले रूप इस्तेमाल करें |
| contactEn | Contact name (English) | संपर्क का नाम (अंग्रेज़ी) |
| contactHi | Contact name (Hindi) | संपर्क का नाम (हिंदी) |
| contactPhone | Contact phone | संपर्क का फ़ोन |
| pinField | PIN (4 digits) | PIN (4 अंक) |
| voicesFound | Voices found: English {e}, Hindi {h} | मिली आवाज़ें: अंग्रेज़ी {e}, हिंदी {h} |
| credits | Open-source credits | ओपन-सोर्स का श्रेय |
| saveWeek | Save this week's file | इस हफ़्ते की फ़ाइल सेव करें |
| eveningText | Evening text | शाम का संदेश |
| sendSms | Send as SMS | SMS से भेजें |
| copied | Copied | कॉपी हो गया |
| savedForLaptop | Saved to Download/StripSense. Send it to the laptop with Office Kit. | Download/StripSense में सेव हो गई। इसे Office Kit से लैपटॉप पर भेजिए। |
| saveFailed | The file could not be saved. Press the button again. | फ़ाइल सेव नहीं हो पाई। बटन फिर से दबाइए। |
| trialMode | Trial mode | परीक्षण मोड |
| trialPickTitle | What is in your hand? | आपके हाथ में क्या है? |
| notHers | Not hers / not taught | उनकी नहीं / सिखाई नहीं गई |
| covered | Hers, but print covered or cut | उनकी है, पर छपाई ढकी या कटी हुई है |
| nextTry | Next try | अगली कोशिश |
| trialLogged | {s}: logged | {s}: दर्ज हुआ |
| noTrials | No tries yet. Switch on Trial mode and scan a strip. | अभी कोई कोशिश नहीं हुई। परीक्षण मोड चालू करके पट्टी स्कैन कीजिए। |
| triesLabel | Tries | कोशिशें |
| rightLabel | Right | सही |
| wrongLabel | Wrong | ग़लत |
| wrongRefusals | Wrong refusals | ग़लत इनकार |
| hersLine | Her strips | उनकी पट्टियाँ |
| notHersLine | Strips that are not hers | जो पट्टियाँ उनकी नहीं हैं |
| coveredLine | Her strips, print covered or cut | उनकी पट्टियाँ, छपाई ढकी या कटी |
| groupCounts | {n} tries: {r} right, {w} wrong, {s} not sure, {x} wrong refusals | {n} कोशिशें: {r} सही, {w} ग़लत, {s} पक्का नहीं, {x} ग़लत इनकार |
| timeToVerdict | Time to verdict | नतीजे तक का समय |
| medianP90 | Median {m} · 90th percentile {p} | बीच का {m} · 90वाँ प्रतिशत {p} |
| byLookAlone | By look alone, a strip that is not hers would have been named as hers {n} times. The print check named {k}. | सिर्फ़ शक्ल देखने पर, जो पट्टी उनकी नहीं है उसे {n} बार उनकी दवा बताया जाता। छपाई की जाँच ने {k} बार बताया। |
| oneIn | 1 in {n} | {n} में 1 |
| lastLook | Last look score | आख़िरी शक्ल का स्कोर |
| saveTrials | Save trials file | कोशिशों की फ़ाइल सेव करें |
| clearTrials | Clear trials | कोशिशें मिटाएँ |
| internetFound | This app has the internet permission | इस ऐप के पास इंटरनेट की अनुमति है |
| permissionsAre | Permissions: | अनुमतियाँ: |
| checkPhone | Check this phone | इस फ़ोन की जाँच करें |
| checkAgain | Check again | फिर से जाँचें |
| chkCameraOk | The camera is allowed | कैमरे की अनुमति है |
| chkCameraNo | The camera is not allowed. Press Open settings and allow it. | कैमरे की अनुमति नहीं है। सेटिंग खोलकर अनुमति दीजिए। |
| chkMicOk | The microphone is allowed | माइक की अनुमति है |
| chkMicNo | The microphone is not allowed. Press Open settings and allow it. | माइक की अनुमति नहीं है। सेटिंग खोलकर अनुमति दीजिए। |
| chkEnOk | An English voice is installed | अंग्रेज़ी आवाज़ मौजूद है |
| chkEnNo | No English voice. Add one in the phone's text-to-speech settings. | अंग्रेज़ी आवाज़ नहीं है। फ़ोन की टेक्स्ट-टू-स्पीच सेटिंग में जोड़िए। |
| chkHiOk | A Hindi voice is installed | हिंदी आवाज़ मौजूद है |
| chkHiNo | No Hindi voice. Hindi answers are shown, and spoken in English. | हिंदी आवाज़ नहीं है। हिंदी जवाब दिखेंगे, पर बोले अंग्रेज़ी में जाएँगे। |
| chkModelOk | The model file is on this phone | मॉडल की फ़ाइल इस फ़ोन में है |
| chkModelNo | The model file is missing. Choose it in Family, Settings. | मॉडल की फ़ाइल नहीं है। परिवार, सेटिंग में जाकर चुनिए। |
| chkGemmaOk | Voice is ready, running on {b} | आवाज़ तैयार है, {b} पर चल रही है |
| chkGemmaNo | Voice could not start. Choose the model file again. | आवाज़ शुरू नहीं हो पाई। मॉडल की फ़ाइल फिर से चुनिए। |
| chkListOk | The list has {n} medicines | सूची में {n} दवाएँ हैं |
| chkTaught | {n} of {m} strips taught | {m} में से {n} पट्टियाँ सिखाई गईं |
| copyNoSpace | Not enough free space for the model file. Free some space and choose it again. | मॉडल की फ़ाइल के लिए जगह कम है। जगह खाली करके फिर से चुनिए। |
| copyFail | The file could not be copied. Choose it again. | फ़ाइल कॉपी नहीं हो पाई। फिर से चुनिए। |
| askEmpty | Hold the microphone and ask, or tap a question below. | माइक दबाए रखकर पूछिए, या नीचे कोई सवाल दबाइए। |
| askLeft | What's left today? | आज और क्या बचा है? |
| askDidTake | Did I take my {n}? | क्या मैंने {n} की खुराक ले ली? |
| askTyped | Type a question… | सवाल लिखिए… |
| send | Send | भेजें |
| thinking | One moment… | ज़रा रुकिए… |
| voiceNoFile | No voice model on this phone yet | इस फ़ोन में अभी आवाज़ का मॉडल नहीं है |
| voiceFailed | Voice could not start | आवाज़ शुरू नहीं हो पाई |
| chooseModel | Choose the model file | मॉडल की फ़ाइल चुनिए |
| copyingModel | Copying the model file… | मॉडल की फ़ाइल कॉपी हो रही है… |
| wrongModelSize | This file is not the size of the model. It may not load. | इस फ़ाइल का आकार मॉडल जैसा नहीं है। हो सकता है यह न चले। |
| gemmaBackend | Runs on | किस पर चल रहा है |
| gemmaLoadTime | Load time | तैयार होने में लगा समय |
| gemmaVoiceCalls | Voice calls | बोलकर पूछे गए सवाल |
| gemmaFailures | Failures | नाकाम |
| gemmaTimeouts | Timeouts | समय निकल गया |
| gemmaMedian | Median call time | बीच का समय |
| gemmaReaderCalls | Second-reader calls | दूसरी जाँच कितनी बार |
| gemmaAgreed | Agreed | सहमत |
| gemmaNotAgreed | Not agreed | असहमत |
| gemmaUnreadable | Unreadable | पढ़ी नहीं गई |
| lastNotAgreed | Last reply that did not agree | आख़िरी जवाब जो मेल नहीं खाया |
| modeOff | Off | बंद |
| modeOn | On | चालू |
| modeStrict | Strict | सख़्त |
| noHindiVoice | No Hindi voice on this phone | इस फ़ोन में हिंदी आवाज़ नहीं है |
| askTitle | Ask | पूछें |
| numbers | Numbers | आँकड़े |
| holdToTalk | Hold to talk | बोलने के लिए दबाए रखिए |
| youAsked | You asked | आपने पूछा |
| voiceReady | Gemma ready | जेमा तैयार |
| voiceLoading | Voice is getting ready | आवाज़ तैयार हो रही है |
| secondAgrees | Second reader agrees: {x} | दूसरी जाँच भी यही कहती है: {x} |
| secondChecking | Second reader is checking… | दूसरी जाँच चल रही है… |
| secondUnsure | Second reader could not confirm the strength | दूसरी जाँच मात्रा पक्की नहीं कर पाई |
| dueNowNotMarked | Due now · not marked yet | अभी का समय · दर्ज नहीं |
| alreadyMarked | Already marked | पहले से दर्ज |
| notOnList | Not on your list | आपकी सूची में नहीं |
| wontName | StripSense won't name it as your medicine | StripSense इसे आपकी दवा नहीं कहेगा |
| cantRead | Can't read the print | छपा हुआ पढ़ा नहीं जा रहा |
| turnOver | Turn it over, or lay it flat in good light | पट्टी पलटिए, या रोशनी में सीधा रखिए |
| notRecognised | Not recognised | पहचान में नहीं आई |
| notOnAnyList | It isn't on any list here | ये यहाँ किसी सूची में नहीं है |
| notShown | Not a strip I was shown | ये पट्टी पहले नहीं दिखाई गई |
| stoppedTitle | Stopped on your list | आपकी सूची में बंद |
| oneAtATime | One strip at a time | एक बार में एक पट्टी |
| says | StripSense says | StripSense कहता है |
| noInternet | This app has no internet permission | इस ऐप के पास इंटरनेट की अनुमति नहीं है |
| bringCloser | Bring it a little closer | थोड़ा पास लाइए |
| helpIntro | Who do you want to call? | किसे फ़ोन करना है? |
| cameraNeeded | StripSense needs the camera to read a strip. | पट्टी पढ़ने के लिए कैमरा चाहिए। |
| micNeeded | StripSense needs the microphone to hear your question. | सवाल सुनने के लिए माइक चाहिए। |
| openSettings | Open settings | सेटिंग खोलें |
| allow | Allow | अनुमति दें |
| torch | Torch | टॉर्च |
| showText | Show read text | पढ़ा हुआ दिखाएँ |
| copy | Copy | कॉपी करें |
| save | Save | सेव करें |
| next | Next | आगे |
| emptyList | No list yet. Load the demo list or import a list file in Family. | अभी कोई सूची नहीं है। "परिवार" में जाकर डेमो सूची या सूची की फ़ाइल डालिए। |

## 6. Tests
- Rendering P1 for the fever tablet at 8:05 am in English gives exactly: `Maa, this is your fever tablet, 500 mg, the morning one. Nothing's marked for this morning yet.`
- A1 for a mark at 8:05 am, read at 8:06 am: `That's your fever tablet, 500 mg. It's already marked for this morning, at 8:05 am, 1 minute ago.`
- P2 for a mark yesterday at 8:10 pm, read at 8:05 am: `Last one was marked yesterday at 8:10 pm, 11 hours ago.`
- V1 with printed `650 mg` and listpart `500 mg`: `Careful: this strip says 650 mg, but yours is 500 mg. This one isn't on your list.`
- Spoken English for 8:05 am is `eight oh five in the morning`; for 8:00 pm, `eight o'clock at night`.
- Spoken Hindi for 8:05 am with `{at_time}` is `सुबह आठ बजकर पाँच मिनट पर`; for 2:00 pm, `दोपहर दो बजे`.
- Spoken Hindi for `500 mg` is `पाँच सौ मिलीग्राम`; for `2.5 mg`, `दो दशमलव पाँच मिलीग्राम`.
- Every ID in sections 2 and 3 renders in both languages with no `{` left in the output.
