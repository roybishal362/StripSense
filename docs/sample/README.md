# The demo list

`list.csv` is the family's list for the demo person, in the format of spec 05 section 3. As shipped it holds five made-up medicines (the same ones the unit tests use).

**At the event, on Friday night, change it to the real strips on your table.** Edit it inside Android Studio (double-click it in the left panel). Never open it in Excel: Excel breaks the Hindi text and changes the times when it saves.
1. Keep the nicknames, times and notes. They tell the story (fever tablet three times a day, and so on).
2. For each row, type what is really printed on the strip you bought:
   - `brand`: the brand word in capitals, without the number. `DOLO`, not `DOLO 650`.
   - `generics`: the generic name in capitals, as printed. Several are separated by `;`.
   - `strengths`: each strength with its unit, as printed. Several are separated by `;`.
   - `release`: `SR`, `ER`, `XR` or similar, only if it is printed. Otherwise leave it empty.
   - `extra_words`: a word that is part of this brand's name on the strip (`PLUS`, `COLD`, `D`). Otherwise empty.
3. The fever tablet row is the **500 mg** strip of your look-alike pair. The 650 mg strip is not on the list. That is the point of the demo.
4. The BP tablet stays STOPPED. Use any strip you have for it; it shows "stopped on your list".
5. The eye drops stay type BUTTON.
6. Leave one of your strips out of the list completely. That is the strip for "I don't recognise this one".
7. If you have no strip for a row, delete that row.
8. Some strips print more than one number with a unit for one ingredient ("Calcium Carbonate 1250 mg eq. to elemental Calcium 500 mg"). Type every one of them in `strengths`, in the order printed. The read-text screen shows you what the phone sees.

Save the file as UTF-8. Stage P04 copies these rows into the app's demo data, and from stage P07 the same file can be imported on the phone.
