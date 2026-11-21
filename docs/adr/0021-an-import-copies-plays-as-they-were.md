---
status: accepted
---

# An Import copies Plays as they were

An Import adds the Plays of an Export as they were at the source: with their UUIDs, and with the Results the source's Game Module calculated (ADR 0010). This Installation's Game Module checks what was entered, and a Play it refuses is left out and listed. A Play whose UUID is already here is skipped and never overwritten. Importing a file twice therefore changes nothing, and two Installations that exchange Exports never undo each other's changes.

## Considered options

- **Scoring each imported Play again with this Installation's Game Module**, as if it were recorded here. Rejected: whenever the two Installations run different versions of a module, the Import would silently change history, which is what ADR 0010 rules out. A Recalculation can still be asked for afterwards.
- **Overwriting a Play that is already here with the file's version.** Rejected: a Play changed in both Installations would need conflicts resolved, which makes this a sync feature, and syncing is not the job.
