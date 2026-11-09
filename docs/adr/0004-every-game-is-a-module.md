---
status: accepted
---

# Every game is a module

Support for each Game lives in its own module, which plugs into a game interface owned by the core. Adding a game means adding a module, never changing the core. This replaces ADR 0003, where most games would have been score sheets defined as data in the app.

We chose modules because they are versioned, testable units with a clear contract, and because building a clean plug-in point is itself a learning goal (ADR 0001). The cost is that adding a game needs someone who writes code. Whether modules are built into the app or loaded at runtime is a separate decision.
