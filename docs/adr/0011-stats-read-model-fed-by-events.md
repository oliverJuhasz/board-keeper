# Statistics keep their own data, fed by events

The `stats` module keeps its own tables. It updates them from application events published by the `plays` module (such as `PlayRecorded` and `PlayRecalculated`) and never reads the plays tables directly. Spring Modulith guarantees those events are delivered.

Querying the plays tables directly would be simpler. But it would couple two modules through the database in a way Spring Modulith can't see or check, and practising event-based module boundaries is one of the project's learning goals (ADR 0001). The cost is that statistics are briefly behind just after a play is saved, and need rebuilding if their tables change shape.
