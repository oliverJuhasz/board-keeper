# Board Keeper

Board Keeper records the board games a group of friends play and how each one ended, so they can look back at their history and statistics.

## Language

### Games and plays

**Game**:
A board game as a product, such as Wingspan or Catan.
_Avoid_: Title, board game. Don't use "game" for one occasion of playing; that is a Play.

**Play**:
One occasion on which a group of Players played a Game.
_Avoid_: Session, match, game, game night

**Game Module**:
The package that adds support for one Game to Board Keeper: how its Plays are recorded and scored.
_Avoid_: Plugin, extension, score sheet

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
An offer from an admin to the person behind a Player to register an Account that claims that Player. Registering is only possible through an Invite.
_Avoid_: Invitation code, sign-up link
