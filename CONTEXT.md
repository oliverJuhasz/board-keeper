# Board Keeper

Board Keeper records the board games a group of friends play and how each one ended, so they can look back at their history and statistics.

## Language

### People

**Player**:
A person who takes part in plays. A Player exists whether or not anyone has registered for them, so people without an Account still appear in results.
_Avoid_: User, participant, member

**Account**:
A registered login to Board Keeper, belonging to one real person.
_Avoid_: User, profile, login

**Claim**:
The link from an Account to the Player who is its owner. Once claimed, that Player's history is the Account owner's history.
_Avoid_: Link, bind
