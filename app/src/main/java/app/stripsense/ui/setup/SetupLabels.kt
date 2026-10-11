package app.stripsense.ui.setup

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
internal val SETUP_LABELS = mapOf(
    "setupTitle" to ("Set up this phone" to "इस फ़ोन को तैयार करें"),
    "setupIntro" to ("A family member should do this once. Nothing you enter leaves this phone." to "परिवार का कोई सदस्य यह एक बार करे। आपकी भरी जानकारी इस फ़ोन से बाहर नहीं जाती।"),
    "setupPerson" to ("Who takes the medicines" to "दवा कौन लेता है"),
    "setupPersonNote" to ("The phone uses this name when it speaks." to "फ़ोन बोलते समय यही नाम लेता है।"),
    "setupContact" to ("Family contact" to "परिवार का संपर्क"),
    "setupContactNote" to ("The Help button calls this number." to "मदद का बटन इसी नंबर पर फ़ोन करता है।"),
    "setupPin" to ("Family PIN" to "परिवार का PIN"),
    "setupPinNote" to ("Protects the medicine list and settings. Choose 4 digits. 1234 is not allowed." to "दवाओं की सूची और सेटिंग की सुरक्षा करता है। 4 अंक चुनें। 1234 नहीं चलेगा।"),
    "setupPinRepeat" to ("Repeat the PIN" to "PIN फिर से लिखें"),
    "setupPinMismatch" to ("The two PINs are different." to "दोनों PIN अलग हैं।"),
    "setupPinNeeded" to ("Choose a PIN to finish." to "पूरा करने के लिए PIN चुनें।"),
    "setupPermissions" to ("What the phone needs" to "फ़ोन को क्या चाहिए"),
    "setupPermissionsNote" to ("The camera reads strips. Notifications ring the reminders. The microphone is for spoken questions." to "कैमरा पट्टियाँ पढ़ता है। सूचनाओं से याद दिलाने की घंटी बजती है। माइक बोले गए सवालों के लिए है।"),
    "setupVoiceNote" to ("The phone speaks its answers with a voice stored on the phone." to "फ़ोन अपने जवाब फ़ोन में मौजूद आवाज़ से बोलता है।"),
    "setupAllow" to ("Allow camera, notifications and microphone" to "कैमरा, सूचना और माइक की अनुमति दें"),
    "setupNotifyOk" to ("Reminders can show notifications" to "याद दिलाने की सूचनाएँ आ सकती हैं"),
    "setupNotifyNo" to ("Notifications are not allowed. Reminders cannot ring." to "सूचनाओं की अनुमति नहीं है। याद दिलाने की घंटी नहीं बजेगी।"),
    "setupExactOk" to ("Reminders can ring at the exact time" to "याद दिलाने की घंटी ठीक समय पर बज सकती है"),
    "setupExactNo" to ("Precise alarms are not allowed. Reminders may be late." to "सही समय के अलार्म की अनुमति नहीं है। घंटी देर से बज सकती है।"),
    "setupVoice" to ("Voice" to "आवाज़"),
    "setupAssistant" to ("On-phone assistant (optional)" to "फ़ोन का सहायक (वैकल्पिक)"),
    "setupAssistantNote" to ("A model file copied to this phone. It reads labels more fully and answers spoken questions. Scanning and reminders work without it." to "इस फ़ोन में कॉपी की गई मॉडल फ़ाइल। यह लेबल ज़्यादा अच्छे से पढ़ती है और बोले गए सवालों के जवाब देती है। स्कैन और याद दिलाना इसके बिना भी चलते हैं।"),
    "setupFinish" to ("Finish setup" to "तैयारी पूरी करें"),
    "storeUnreadableTitle" to ("Saved medicines could not be opened" to "सेव की हुई दवाएँ नहीं खुलीं"),
    "storeUnreadableBody" to ("The saved file on this phone could not be read. Nothing was changed or deleted. Reminders are paused until this is fixed. Ask your family for help." to "इस फ़ोन की सेव फ़ाइल नहीं पढ़ी गई। कुछ भी बदला या हटाया नहीं गया है। ठीक होने तक याद दिलाना रुका है। परिवार से मदद लें।"),
    "storeRetry" to ("Try again" to "फिर कोशिश करें"),
    "storeRetryFailed" to ("It still cannot be read." to "फ़ाइल अब भी नहीं पढ़ी गई।"),
    "storeStartEmpty" to ("Start with an empty list" to "खाली सूची से शुरू करें"),
    "storeStartEmptyNote" to ("The unreadable file stays on this phone, but the medicines and history in it will not be shown. Every medicine must be added again." to "न पढ़ी गई फ़ाइल फ़ोन में रहेगी, पर उसकी दवाएँ और इतिहास नहीं दिखेंगे। हर दवा फिर से जोड़नी होगी।"),
    "storeStartEmptyConfirm" to ("Yes, start empty" to "हाँ, खाली शुरू करें"),
    "storeStartEmptyFailed" to ("That did not work. Free phone storage and try again." to "यह नहीं हो पाया। फ़ोन में जगह खाली करके फिर कोशिश करें।"),
)
