# Local reminder and timing repair

The app previously had no AlarmManager, receiver or notification implementation. A saved 07:15 schedule could therefore never ring in the background. The new local reminder implementation is included in the current source and assembled APK; it does not rely on a server or load a model in the alarm receiver.

## Behavior

One next AlarmManager wakeup covers the saved active medicines. With precise-alarm access it uses an alarm-clock wakeup; otherwise an inexact idle-capable fallback is shown as possibly delayed. Notification access, blocked channel and silent channel are surfaced in Family > Settings and as a readiness warning on Today.

The ledger persists attempts and snooze times before a repeat is posted. Defaults are two alerts, ten minutes apart; the caregiver can choose one to three alerts and a 5, 10, 15 or 30 minute interval. No unlimited ringing. Chime is eight seconds. Closed windows, stopped medicines, already marked slots and Not sure slots do not ring. No first-time historical alarm more than five minutes past its scheduled time. Only explicit intake confirmation writes a TAKEN event, with source REMINDER_TAP; the store acknowledges durable write and handles duplicate taps without another event. Weekly and evening exports report the source.

Receiver handles reboot, app replacement, clock/time-zone changes and precise-alarm permission grants. App resume and store changes reconcile the next alarm and cancel outdated notifications. Retry records older than two dosing days are pruned. Notification tap opens the actual medicine and slot; no synthetic dose is inserted by the labelled delivery test.

Scan and Today distinguish scheduled now, later, already marked, no saved schedule and unconfirmed/uncertain. The latest scan also highlights the next upcoming medicine in green when none is scheduled now. Its label says Next at the actual time; a future green box never enables an early dose prompt. Marked doses are grey with a check and overdue/unconfirmed doses are red. The scheduled-now display ends after fifteen minutes. This display grace period does not change the prescribed time or authorize a late dose. The underlying reviewed dose window remains the existing bounded core rule. A future strip answer uses the fixed W1 sentence. Recent other-slot marks block green status. Partial reference candidates never become confirmed identity or intake questions.

## Checks

280 JVM checks pass, including twenty new checks for early/later/next timing, no premature prompt, retries, snooze, expiry, invalid policy, recent marks, duplicate confirmation, reopening history and disk failure. Earlier six native teaching checks remain applicable. Debug APK builds offline. No dependency or tool version changed. INTERNET is absent; POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM and RECEIVE_BOOT_COMPLETED are present.

Notification and precise-alarm access were observed enabled in the phone UI. Screen-off alarm delivery is pending because the connected phone switched to another app repeatedly during the labelled test setup. No delivery test was reported as passed. Reboot, battery-saver, permission revocation and real daily-repeat acceptance remain pending.

## Device acceptance without fake medicines

1. Open Family > Settings, permit notifications and precise alarms, and check that the channel has sound. Use Test an alarm in 15 seconds, turn the screen off, and verify both arrival and an audible chime. The test writes no medicine or intake event.
2. With the family agreeing on a real prescribed future time, close the app and check the dose notification. Tap I've taken it only after actual intake. Verify local history and repeated notification taps; test snooze on an appropriate real reminder. Keep the phone offline throughout.
3. Reboot before a future reminder, revoke/regrant exact or notification access, try battery-saving mode and inspect the readiness warning. Verify removing/stopping or marking a medicine cancels its pending reminder. Never loosen dose safety rules to make a demo green.

Three existing user entries are retained. Two P-500-related entries have different times and mixed manual identities; their schedule and identity need caregiver review before reliable medicine-specific recognition. No silent merge or schedule correction was made. Teach > Read label and update details rereads the saved photo where one exists. The user must check and confirm that correction. Model extraction cannot infer a prescription from a strip.

## Remaining family work

OFFLINE-FAMILY-PLAN.md distinguishes current local functionality from planned patient profiles, authenticated device linking, remote scheduling acknowledgements, event exchange, caregiver escalation and timestamped battery/connectivity. These remote features need a real configured service and a second device. They are not claimed as completed. The current family PIN is local access control, not an online account.
