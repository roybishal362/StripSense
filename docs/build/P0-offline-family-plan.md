# Offline family care plan

The core runs on each parent's phone. A family account adds optional exchange between phones; it never becomes a condition for ringing a saved alarm or recording an intake confirmation. No cloud AI is needed for strip teaching or recognition.

## What needs a connection

| Task | Offline behavior | Connection needed |
|---|---|---|
| Teach a medicine and save reference photos | Read, review and save on this phone | No, once the local models are installed |
| Recognize strips and show saved schedule timing | Camera, OCR, reference comparison and schedule checks stay local | No |
| Alarms, bounded retries and snooze | Use the approved schedule saved on the parent's phone | No |
| Confirm intake and view this phone's history | Save locally first; never wait for a server | No |
| Edit a schedule on this phone | Caregiver review, durable save and alarm reconciliation | No |
| View already downloaded prescriptions and family history | Show cached content and its last sync time | No |
| Create online family access, invite a distant child and authorize a new device | One-time authenticated enrollment | Yes |
| Send a new schedule from another city | Queue on the child's phone until both sides can exchange updates | Yes, at transfer time |
| Monitor another phone's confirmations, battery and connectivity | Show the last received status, with its age | Yes for fresh information |
| Alert a caregiver in another city | Server evaluates missing confirmation and queues a push alert | Yes for remote delivery |
| Upload or download a prescription or reference photo | Capture and save locally; queue the protected transfer | Yes for transfer only |
| Nearby transfer between trusted phones | Future encrypted signed bundle or peer transfer | No internet, but needs local communication |
| Call a caregiver | Open the phone dialer | Cellular calling service; not necessarily internet |

Having the same account does not carry data between disconnected phones. Information needs an internet, local wireless or deliberate file-transfer path. No app can show a fresh battery level or remotely delivered confirmation from a phone that has no communication path.

## Family identity and minimal parent setup

Use one family space, separate identities and roles, and separate patient profiles. The child owns the family and invites trusted caregivers. Mom, Dad and Grandma each have a profile. Each parent device is assigned to its patient profile and opens a large, simple Today screen. It does not show a caregiver configuration form by default.

The child signs in on their own device. A parent's phone joins once with a short-lived invitation or QR code approved by the child. Do not share one password across the family. A caregiver can lose access without changing every other person's login. Give parent devices scoped permission to read their own schedule and write their own confirmations. Caregiver actions name the actor and patient.

Standalone local use needs no online account. A PIN is a local caregiver lock, not remote authentication. A PIN cannot prove who owns a cloud family.

## Local storage and reliable schedule activation

Keep medicine identity, reviewed reference photos, approved schedule versions, dose events and reminder policy locally. Add stable familyId, patientId, deviceId, scheduleVersion and eventId in a migration that keeps today's user IDs and history. A private parent device is the single reminder owner for that profile by default, preventing two linked phones ringing duplicate sequences.

The caregiver creates a draft from a prescription, reviews it and publishes a version with its effective time and explicit time zone. The parent's phone downloads, validates, saves it durably, cancels superseded alarms and schedules the next local wakeup. Only then does it acknowledge installation to the caregiver. The child sees Draft, Waiting for device, Installed on device or Failed; never a false success from a server upload alone.

An unreceived change does not silently alter the parent's cached schedule. A stopping instruction queued while the parent's phone is offline must show as not delivered on the caregiver's phone. For urgent changes the caregiver must contact the parent using another available channel. A backend cannot stop an alarm on an unreachable phone.

Require an explicit review for conflicting edits. Use a server revision check instead of silently overwriting a newer prescription with a stale device. Store a prescription's time zone; do not reinterpret an India schedule merely because the caregiver travels to another time zone. Device clock changes cause alarm reconciliation and should surface an unusual clock discrepancy when syncing.

## Confirmation and synchronization

An intake tap creates a local append-only event with a unique eventId, patientId, medicineId, scheduleVersion, doseSlot, reportedAt, deviceId and source. Show success only after durable local storage. A local outbox retries exchange when a connection returns. Server deduplicates by eventId; the client tracks a sync cursor. Never create two intake events because a notification tap or upload was repeated.

Keep scheduled time, alarm receiver arrival, notification posted, user confirmation and sync receipt separate. Posting a notification is not proof that it was heard. A tap is reported intake, not proof of swallowing. History remains available offline. A caregiver's cached history clearly names its last successful update.

Use these distinct states: upcoming, awaiting confirmation, reported taken, explicitly skipped, not sure and unconfirmed. Delivery status is separate: installed, permission blocked, receiver reached or device status unavailable. Add an explicit skip event when that workflow is built; do not use Not sure to mean skipped. Expired reminders remain unconfirmed rather than being labeled a medically missed dose automatically.

## Bounded escalation and device status

The current local build supports a bounded parent sequence. The proposed default family sequence is two parent reminders, then up to three caregiver alerts at a reviewed interval. Parent retries are generated locally without internet. Caregiver escalation jobs run on the service because the parent's phone may be unreachable. The caregiver receives a notification only when their phone also has a communication path.

If no sync arrives, say "No confirmation received. Device last updated at ...". Do not claim the medicine was missed or the phone switched off. When a delayed confirmation syncs, resolve the alert and show the actual reported time. Queueing an alert does not equal delivery. Cancel remaining retries after confirmation, explicit skip or the configured expiry. Never ring indefinitely or recommend taking a delayed dose automatically.

Battery percentage, charging state and a last successful communication time are timestamped observations, with the parent's consent. Periodic background exchange is best effort under Android power controls; do not promise minute-by-minute monitoring. The service marks the status stale when the expected contact is absent, not "switched off". Low battery suggestions can run locally from the next saved reminder; remote battery alerts require a recent report.

## Privacy and optional internet

Retain on-device OCR and Gemma processing. Upload reviewed structured schedules and events, not continuous camera frames or audio. Transfer prescriptions or reference photos only for an authorized family need. Protect transport, storage and per-patient access. Keep server credentials out of the APK. Revocation stops future access; it cannot erase an offline device instantly. Plan for account deletion, export and secure local cleanup before public deployment.

The current core APK has no INTERNET permission. The real family extension needs an explicitly enabled sync transport and its permission. Offline capability means it works without a connection; it does not mean that an optional connected family mode can exchange data without a transport. Retain a standalone mode and make family sync optional. Existing CSV/file exchange is manual; it is not live account synchronization or remote escalation.

## Implementation order and honest scope

1. Repair and check the existing local core: durable teaching, exact printed identity, multiple-strip schedule timing, background alarms, permissions, confirmation, bounded retry and local history. This is the immediate evaluation priority.
2. Add patient-scoped local profiles and migrate existing records without deleting them. Make parent mode simple and caregiver changes reviewed. Ensure reminder identity includes patient and device ownership.
3. Configure a real authenticated backend, family roles and device invitations. Implement versioned schedules, durable outbox, deduplicated events and installed-on-device acknowledgement. A backend project and a second phone are needed to prove remote management.
4. Add server escalation, daily family dashboard, direct calling and timestamped device telemetry. Exercise loss of connectivity and late event arrival before claiming live monitoring.
5. Add protected prescription storage and reviewed OCR schedule drafts. Pattern reports describe observed confirmations; they never change prescriptions. Predictions are a later evaluation task, not a substitute for reliable reminders.
6. Add optional nearby trusted transfer only if it solves a real setup need. Use authenticated encrypted transfer with replay protection and explicit review. Do not roll an unreviewed custom cryptographic protocol for the evaluation.

Steps 2 onward are planned, not implemented in the current single-patient local app. No sample family dashboard should pretend to represent Mom or Dad. Cloud configuration remains absent.

## Genuine iQOO contribution

The connected iQOO 15 runs local GPU extraction. Two actual saved strip photos took 4,384 and 4,724 milliseconds in the native check. That is limited performance evidence, not an accuracy benchmark. Its camera supplies label photos; this app's capture pipeline uses bounded working images rather than claiming every capture is full 50 MP.

The official India specifications list a 7,000 mAh battery and OriginOS 6 based on Android 16. Battery capacity can reduce charging pressure. Local reminder delivery still depends on notification permission, precise-alarm permission, sound settings and phone state. Device status monitoring is built with standard Android APIs; there is no claimed private OriginOS AI integration or swallowed-dose detector.

Sources: [iQOO specifications](https://www.iqoo.com/in/products/param/iqoo15), [Android alarms](https://developer.android.com/develop/background-work/services/alarms), [Android exact-alarm access](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).
