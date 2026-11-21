---
status: accepted
---

# An import copies Plays as they were

An import adds the Plays of an export as they were at the source: with their UUIDs, and with the Results the source's Game Module calculated (ADR 0010). This installation's Game Module checks what was entered, and a Play it refuses is left out and listed. A Play whose UUID is already here is skipped and never overwritten. Importing a file twice therefore changes nothing, and two installations that exchange files never undo each other's changes.

## Considered options

- **Scoring each imported Play again with this installation's Game Module**, as if it were recorded here. Rejected: whenever the two installations run different versions of a module, the import would silently change history, which is what ADR 0010 rules out. A Recalculation can still be asked for afterwards.
- **Overwriting a Play that is already here with the file's version.** Rejected: a Play changed in both installations would need conflicts resolved, which makes this a sync feature, and syncing is not the job.
