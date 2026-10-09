# The notes in this folder

These are the design notes for StripSense. They were written before the hackathon. All the app's code is written during it, from these notes.

| Folder | What is in it |
|---|---|
| `spec/` | What the app must do. Seven files: reading a strip and deciding (01), the dose rules (02), every sentence in English and Hindi (03), voice and the model (04), files and the family's side (05), the screens (06), the library versions and call shapes that were compiled (07). |
| `build/` | The work, cut into stages P01 to P12, one file each, plus three optional stretch stages. (Stage P00, making the empty project, was done by hand.) Each file says what to build, what not to touch, and how to tell it works. |
| `design/` | Pictures of the screens from an earlier web mock-up. A visual reference only. Where a picture and `spec/06` differ, the spec is right. |
| `sample/` | `list.csv`, the demo medicine list, and a note on changing it to the real strips on the table. |

The order of work is the order of the files in `build/`. A stage is finished when its "Done when" list passes on the phone.
