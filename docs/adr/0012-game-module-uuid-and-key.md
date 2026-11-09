# Game Modules are identified by a UUID, with a readable key alongside

Every Game Module has a UUID, generated once when the module is created and kept as a constant in its code. Plays store this UUID, so it must never change. Each module also has a readable key, such as `calico`, used for translation file names, folders and logs. The app refuses to start if two modules share either one.

Readable keys alone would be enough inside one repository. Once the project is public, though, a fork or a self-hoster's own module could use a key that the main repository later gives to a different module, and their stored Plays would then point at the wrong one. Some different games also share a name.
