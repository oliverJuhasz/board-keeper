# Results are stored, and recalculated only on request

A Play stores both the values that were entered and the Results its Game Module calculated from them at the time. When a module's scoring changes, for example after a bug fix, existing Results stay as they were. They change only when someone recalculates that Play, or an Admin recalculates every Play of a Game. Recalculation uses the Expansions recorded on the Play, because Expansions can change scoring substantially.

We rejected calculating Results on the fly. A code change would then silently rewrite history, and statistics would have to run module code instead of reading stored values.
