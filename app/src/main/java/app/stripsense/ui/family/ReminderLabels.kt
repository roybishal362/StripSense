package app.stripsense.ui.family

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
internal val REMINDER_LABELS = mapOf(
    "evaluationTitle" to ("Evaluation rehearsal" to "प्रस्तुति का अभ्यास"),
    "evaluationNotice" to ("SIMULATED EXAMPLE. No camera identification or real intake records. Return to Today for your medicines." to "अभ्यास का उदाहरण। कैमरे से पहचान या असली खुराक का रिकॉर्ड नहीं। अपनी दवाओं के लिए आज पर लौटें।"),
    "evaluationNow" to ("Example A · Scheduled now · 10:00 am" to "उदाहरण A · अभी का समय · सुबह 10 बजे"),
    "evaluationMarked" to ("✓ Example A · Confirmation marked" to "✓ उदाहरण A · पुष्टि दर्ज"),
    "evaluationLater" to ("Example B · Later at 10:30 am" to "उदाहरण B · बाद में सुबह 10:30 बजे"),
    "evaluationNext" to ("Example B · Next at 10:30 am; not due yet" to "उदाहरण B · अगली सुबह 10:30 बजे; अभी समय नहीं"),
    "evaluationPast" to ("Example C · 9:00 am passed; not confirmed" to "उदाहरण C · सुबह 9 बजे का समय बीता; पुष्टि नहीं"),
    "evaluationConfirm" to ("Simulate confirmation for Example A" to "उदाहरण A की पुष्टि का अभ्यास"),
    "evaluationReset" to ("Reset rehearsal" to "अभ्यास फिर शुरू करें"),
    "medicineReminder" to ("Medicine reminder" to "दवा का अलार्म"),
    "confirmationPending" to ("Confirmation pending" to "पुष्टि बाकी है"),
    "reportedTaken" to ("I've taken it" to "मैंने ले ली है"),
    "remindLater" to ("Remind me later" to "फिर याद दिलाएँ"),
    "reminderSettings" to ("Offline medicine alarms" to "बिना इंटरनेट दवा के अलार्म"),
    "reminderOffline" to ("The phone rings at each saved time, even with no network." to "फ़ोन हर सेव समय पर बजता है, नेटवर्क न हो तब भी।"),
    "reminderEnabled" to ("Medicine alarms enabled" to "दवा के अलार्म चालू"),
    "reminderDisabled" to ("Medicine alarms are turned off." to "दवा के अलार्म बंद हैं।"),
    "reminderInterval" to ("Ask again every (minutes)" to "कितने मिनट में फिर पूछें"),
    "reminderWindowNote" to ("The phone stops asking 30 minutes after the saved time. The dose then stays shown as not confirmed." to "सेव समय के 30 मिनट बाद फ़ोन पूछना बंद कर देता है। खुराक तब बिना पुष्टि के दिखती रहती है।"),
    "reminderNotSure" to ("I'm not sure" to "पक्का नहीं पता"),
    "reminderPermissions" to ("Allow notifications" to "सूचनाओं की अनुमति दें"),
    "reminderExact" to ("Allow precise alarms" to "सही समय के अलार्म की अनुमति दें"),
    "reminderSound" to ("Check alarm sound" to "अलार्म की आवाज़ जाँचें"),
    "reminderReady" to ("Notifications and precise alarms are allowed. Check sound and screen-off delivery on this phone." to "सूचनाओं और सही समय के अलार्म की अनुमति है। आवाज़ और बंद स्क्रीन पर अलार्म जाँचें।"),
    "reminderBlocked" to ("Alarms need notification permission and an enabled notification channel." to "अलार्म के लिए सूचनाओं की अनुमति और चालू चैनल चाहिए।"),
    "reminderInexact" to ("Precise alarm permission is missing. Reminders may be delayed." to "सही समय के अलार्म की अनुमति नहीं है। देरी हो सकती है।"),
    "reminderSilent" to ("The reminder channel is silent. Turn its sound on in phone settings." to "दवा के चैनल की आवाज़ बंद है। फोन सेटिंग में चालू करें।"),
    "reminderTest" to ("Test an alarm in 15 seconds" to "15 सेकंड में अलार्म जाँचें"),
    "reminderTestPending" to ("Delivery test scheduled. Turn the screen off and listen." to "जाँच का अलार्म रखा गया। स्क्रीन बंद करके सुनें।"),
    "reminderTestTitle" to ("Reminder delivery test" to "अलार्म की जाँच"),
    "reminderTestBody" to ("This checks sound and screen-off delivery. It is not a medicine reminder." to "यह आवाज़ और बंद स्क्रीन की जाँच है। यह दवा लेने का अलार्म नहीं है।"),
    "reminderTestReceived" to ("Last delivery test reached the alarm receiver at {t}. Check that you heard the sound." to "पिछली जाँच {t} पर फोन तक पहुँची। आवाज़ सुनी या नहीं, जाँचें।"),
    "reminderSaveFailed" to ("Could not save this change. Try again." to "बदलाव सेव नहीं हुआ। फिर कोशिश करें।"),
    "reminderExpired" to ("This reminder can no longer be confirmed here. Check the current history and ask your family about late doses." to "यहाँ इस अलार्म की पुष्टि अब नहीं होगी। इतिहास जाँचें और देर की खुराक के लिए परिवार से पूछें।"),
    "reminderSetup" to ("Check reminder setup" to "अलार्म की सेटिंग जाँचें"),
)
