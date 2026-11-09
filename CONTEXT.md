# Board Keeper

Board Keeper records the board games a group of friends play and how each one ended, so they can look back at their history and statistics.

## Language

### Games and plays

**Game**:
A board game as a product, such as Wingspan or Catan. Each Game is scored by one Game Module.
_Avoid_: Title, board game. Don't use "game" for one occasion of playing; that is a Play.

**Expansion**:
An add-on to a Game that can change how it is played and scored, for example its player count or its Score Categories.
_Avoid_: Add-on, extension, DLC

**Play**:
One occasion on which a group of Players played a Game to the end, together with the Expansions they used. A game that was abandoned part-way is not a Play.
_Avoid_: Session, match, game, game night

**Cooperative**:
Describes a Play in which all Players win or lose together against the game. The opposite is competitive.
_Avoid_: Team game

**Recording Mode**:
How much of a Play's outcome is recorded. **Points** records scores: one per Player, or one group score in a Cooperative Play. **Winner only** records just who won. A Game Module sets the default, and the recorder can switch it for each Play.
_Avoid_: Scoring type, outcome type

### Scoring

**Game Module**:
The package that adds support for one Game to Board Keeper: how its Plays are recorded and scored.
_Avoid_: Plugin, extension, score sheet

**Generic Module**:
The built-in Game Module for any Game that has no module of its own. It records only totals, or only the winner.
_Avoid_: Default game, fallback

**Score Category**:
One named value recorded or calculated for each Player in a Play, such as "Eggs" in Wingspan.
_Avoid_: Score line, field

**Tie-breaker**:
How Players with equal totals are put in order. It is either a Score Category that doesn't count toward the total (leftover food in Wingspan) or, where the Game allows it, the recorder's own choice. Players still tied share a Placing.
_Avoid_: Tiebreak rule

**Result**:
What one Player got in one Play: their Score Categories, total and Placing, or simply whether they won.
_Avoid_: Score, outcome

**Placing**:
A Player's position at the end of a competitive Play. Tied Players share a Placing, and everyone in first Placing has won.
_Avoid_: Rank, position

**Recalculation**:
Running a Game Module's scoring again over the values recorded for past Plays, replacing their stored Results. It happens only on request.
_Avoid_: Rescoring

### People

**Player**:
A person who takes part in plays. A Player exists whether or not anyone has registered for them, so people without an Account still appear in results.
_Avoid_: User, participant, member

**Account**:
A registered login to Board Keeper, belonging to one real person. Every Account claims exactly one Player.
_Avoid_: User, profile, login

**Claim**:
The link from an Account to the Player who is its owner. Once claimed, that Player's history is the Account owner's history.
_Avoid_: Link, bind

**Invite**:
An offer from an Admin to the person behind a Player to register an Account that claims that Player. Registering is only possible through an Invite.
_Avoid_: Invitation code, sign-up link

### Access

**Permission**:
One thing an Account may do, such as "delete any Play".
_Avoid_: Grant, right, authority

**Role**:
A named set of Permissions given to Accounts. An Account can hold several Roles and may do anything any of them allows.
_Avoid_: Permission set, group

**Admin**:
The built-in Role that holds every Permission; also any Account holding it. At least one Account always holds it.

**Member**:
The built-in Role an Invite gives unless the Admin picks another.
