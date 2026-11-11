# Board Keeper

Board Keeper records the board games a group of friends play and how each one ended, so they can look back at their history and statistics.

## Language

### Games and plays

**Game**:
A board game as a product, such as Wingspan or Catan. Each Game is scored by one Game Module.
_Avoid_: Title, board game. Don't use "game" for one occasion of playing; that is a Play.

**Expansion**:
An add-on product for a Game, such as Oceania for Wingspan. It can change how the Game is played and scored, for example its player count or its Score Categories.
_Avoid_: Add-on, extension, DLC

**Variant**:
A setup option of a Game, chosen for each Play, that can change how it is scored. It is a choice between options (Harmonies' board side A or B), an on/off switch (Calico's family variant), or a number in a range (the adversary level in Spirit Island).
_Avoid_: Mode, house rule, option

**Setup**:
The Expansions and Variants chosen for a Play as a whole. Choices for individual Players are their Player Setup.
_Avoid_: Configuration

**Player Setup**:
The choices made for one Player in a Play, such as their Spirit and its aspect in Spirit Island, or their Wonder and its side in 7 Wonders. The Game Module declares which choices exist, and may allow a Player more than one, such as two Spirits in a Solo Play.
_Avoid_: Character, faction, identity

**Play**:
One occasion on which a group of Players played a Game to the end, with a particular Setup. A game that was abandoned part-way is not a Play.
_Avoid_: Session, match, game, game night

**Cooperative**:
Describes a Play in which all Players win or lose together against the game. The opposite is competitive.
_Avoid_: Team game

**Recording Mode**:
How much of a Play's outcome is recorded. **Points** records scores: one per Player, or one group score in a Cooperative Play. **Winner only** records just who won. A Game Module sets the default, and the recorder can switch it for each Play.
_Avoid_: Scoring type, outcome type

**Solo Play**:
A Play with a single Player. Whether a Game supports it, and how it is recorded (against an automated opponent, against a target, or for score only), is up to its Game Module.
_Avoid_: Single-player game

**Victory Type**:
How a Play was won, for Games that can be won in more than one way, such as on points or by scientific supremacy in 7 Wonders Duel.
_Avoid_: Win condition, ending

### Scoring

**Game Module**:
The package that adds support for one Game to Board Keeper: how its Plays are recorded and scored.
_Avoid_: Plugin, extension, score sheet

**Generic Module**:
The built-in Game Module for any Game that has no module of its own, which is every Game someone adds to the catalogue by hand. It records only totals, or only the winner.
_Avoid_: Default game, fallback

**Score Category**:
One named value recorded or calculated for each Player in a Play, such as "Eggs" in Wingspan.
_Avoid_: Score line, field

**Tie-breaker**:
How Players with equal totals are put in order. It is either a Score Category, whether or not it counts toward the total (civilian points in 7 Wonders Duel count, leftover food in Wingspan doesn't), or, where the Game allows it, the recorder's own choice. Players still tied share a Placing.
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

**Setup Link**:
The one-time link, written to the server's log while no Account exists, that creates the first Admin.
_Avoid_: Install link, bootstrap token

**Invite**:
An offer from an Admin to the person behind a Player to register an Account that claims that Player: a link that works once and for a limited time. Registering is only possible through an Invite.
_Avoid_: Invitation code, sign-up link

**Reset Link**:
A one-time link that lets the owner of an Account choose a new password, created for them by an Admin. Board Keeper sends no email, so this is how a forgotten password is solved.
_Avoid_: Recovery link, forgot-password link

**Disabled Account**:
An Account that can no longer sign in. Its Player and its history stay, and it can be enabled again.
_Avoid_: Deleted account, banned, locked

### Access

**Permission**:
One thing an Account may do, such as "delete any Play".
_Avoid_: Grant, right, authority

**Role**:
A named set of Permissions given to Accounts. An Account can hold several Roles and may do anything any of them allows.
_Avoid_: Permission set, group

**Admin**:
The built-in Role that holds every Permission; also any Account holding it. Only an Admin can manage Roles, Accounts and Invites. At least one Account always holds the Role.

**Member**:
The built-in Role an Invite gives unless the Admin picks another.

## The main terms in the interface's other languages

| English | Dutch | Hungarian |
|---|---|---|
| Game | spel | játék |
| Play | partij | parti |
| Player | speler | játékos |

The interface addresses people informally in both: "je" in Dutch, tegezés in Hungarian.
