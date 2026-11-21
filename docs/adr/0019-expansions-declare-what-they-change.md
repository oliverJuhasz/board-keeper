---
status: accepted
---

# Expansions declare what they change on the Score Pad

An Expansion can change a Game's basic rules. Lords of Waterdeep is for two to five players, but six can play with Scoundrels of Skullport, and Leaders brings Wonder boards to 7 Wonders that can't be chosen without it. A Game Module says this in its score pad, on the Expansion and on the Player Setup option, instead of in code of its own that checks a Play. The frontend draws the form from the same description, so it follows the rule while the Setup is chosen: a sixth Player can be added once the Expansion is ticked, and an Expansion's Wonder boards are offered only with it. The module checks a Play against the same description, so the form and the module can't disagree.

## Considered options

- **A check written in the module**, a method that `StandardGameModule` would call before scoring, with a refusal of the module's own. Rejected: it is cheaper to add, but the form would learn about the rule only when the server refused a preview, and each module would write the same kind of check its own way.

## Consequences

- Each new kind of change an Expansion can make takes a change to `game-api`, the API and the form. Lowering the fewest Players, or a way of playing alone that comes with an Expansion, waits for a Game that needs it.
- The form asks for the Expansions before the Players, because they decide how many Players can be added and what can be chosen for each.
- A Play recorded before an option was tied to its Expansion, such as Roma in 7 Wonders without Leaders ticked, is refused when it is recalculated. It keeps its Results until it is corrected (ADR 0010).
