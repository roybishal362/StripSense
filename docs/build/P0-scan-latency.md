# Live scan latency repair

The prior scan ran CPU embedding for every crop before its cheap fingerprint, then withheld every checked result until four crops finished. It also waited at least 800 milliseconds before the next batch. The selected answer repeated this eager embedding.

LookCheck.withFallback applies the existing fingerprint acceptance first. If that produces NotShown, it runs the existing embedding fallback with the same threshold. Wrong print, untaught medicines and disabled checks cannot trigger or bypass that logic. No identity rule or similarity threshold was relaxed. Five checks verify no unnecessary embedding, necessary fallback, missing/incorrect feature refusal and conflicting-print preservation.

The live pipeline publishes each crop result immediately, always processes full print matches first and limits expensive incomplete-region comparisons to two per batch. There is one active comparison job, with no queue. Next batch can start after 200 rather than 800 milliseconds. Completed results are discarded when their region or medicine/reference data changed. Selection also uses the lazy fallback; measurement trials retain their embedding scores.

This removes avoidable work and fixed delay. No end-to-end camera speed multiplier is claimed without an actual held-strip latency trial. Logs contain only scene reference duration and whether the print was full, never frames or transcripts.

The current phone labels are Parawel 325 mg, apex 500 mg and a new Neoberry entry with nine ingredients/strengths. Parawel's saved identity lacks the DT variant visible in its saved photo, and P-500 still uses the APEX manufacturer name. The user removed the older extra P-500 row. No user identities, prescribed times or history were overwritten by this repair. Visible conflicts now show Print versus Saved instead of a generic waiting impression.

Neoberry needs readable print for all its saved ingredients and strengths to produce full identification. A front name without composition is incomplete evidence, not a model-speed problem. Do not make a learned-looking fragment automatically name a medicine to reduce waiting. If the camera shows no readable text, show its printed side closer in steady light; waiting alone cannot fill missing evidence.

Build and core checks pass with 285 tests. Earlier native teaching checks are retained as earlier evidence, not a live scan timing benchmark. Reopen Which strip to take? after installing the APK. Try the same physical strips and record time to first stable identity/color under the same light and distance. Check wrong-strength refusal again. The labelled evaluation rehearsal remains separate from real medicine/history.
