# Game modules are built into the app

Game modules are compiled into the application and shipped with it, so adding a game means a new release. The game interface lives in its own small Maven module, which game modules depend on.

We rejected loading modules at runtime, for example a backend JAR and a frontend bundle dropped into a folder on the NAS. That needs runtime class loading, a versioned plugin interface, and React code loaded at runtime that shares the app's copy of React, which is a large project of its own. New games will be added rarely, and a release is cheap once the build pipeline exists. Keeping the interface separate leaves runtime loading possible later.
