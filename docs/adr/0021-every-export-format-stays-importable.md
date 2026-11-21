---
status: accepted
---

# Every version of the Export format stays importable

An Export says which version of its format it uses: a whole number, raised only by a change that an older Board Keeper could not read. Board Keeper imports files of its own format version and of every older one, and refuses a newer one with the advice to update first. Tests keep a sample file of each version, so a change that breaks an older one fails the build.

This is the first promise of compatibility across versions in Board Keeper. The API is deliberately unversioned until Board Keeper is published (see Later in the roadmap). An Export is different, because it is a file people keep, and the two Installations of an Import may run different versions. We rejected importing only the current format, with the advice to export again after updating. The Installation that wrote a file may no longer exist, and two friends' Installations would have to be updated in step before they could exchange Plays.

## Consequences

- A change to the format either keeps older files readable, as adding an optional field does, or raises the version and keeps a reader for the older ones.
