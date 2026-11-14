import type { GameModule } from "@/api/client"

// The Game Modules of the pretend backend, as the real one describes them, and their English
// texts. Kept apart from the other fixtures because the pretend server itself needs them.

export const genericModule: GameModule = {
  id: "38b119d2-8f9f-4e20-8068-cba305c06cdd",
  key: "generic",
  scorePad: {
    minPlayers: 1,
    expansions: [],
    victoryTypes: [],
    variants: [
      { key: "cooperative", kind: "ON_OFF", defaultValue: "false", soloOnly: false },
      { key: "lowestScoreWins", kind: "ON_OFF", defaultValue: "false", soloOnly: false },
    ],
    categories: [{ key: "total", countsTowardTotal: true, step: 1, soloOnly: false }],
    recorderDecidesTies: true,
  },
}

export const calicoModule: GameModule = {
  id: "d392b4e5-a531-44c4-85c7-369783d9be5c",
  key: "calico",
  scorePad: {
    minPlayers: 1,
    maxPlayers: 4,
    expansions: [],
    victoryTypes: [],
    variants: [{ key: "family", kind: "ON_OFF", defaultValue: "false", soloOnly: false }],
    categories: [
      {
        key: "designGoals",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        onlyWhen: { key: "family", value: "false" },
        soloOnly: false,
      },
      { key: "cats", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      { key: "buttons", countsTowardTotal: true, min: 0, step: 3, soloOnly: false },
    ],
    recorderDecidesTies: true,
    solo: { kind: "FOR_SCORE_ONLY" },
  },
}

/** Played alone against the Automa, whose score is a row of the pad in a Solo Play only. */
export const wyrmspanModule: GameModule = {
  id: "19fc28f0-730d-49aa-8157-730bf2ee89dc",
  key: "wyrmspan",
  scorePad: {
    minPlayers: 1,
    maxPlayers: 5,
    expansions: [],
    victoryTypes: [],
    variants: [
      {
        key: "automaLevel",
        kind: "CHOICE",
        options: ["1", "2", "3"],
        defaultValue: "1",
        soloOnly: true,
      },
      { key: "ravelMode", kind: "ON_OFF", defaultValue: "false", soloOnly: true },
    ],
    categories: [
      { key: "dragonGuild", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      { key: "printedDragons", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      { key: "eggs", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      { key: "automa", countsTowardTotal: false, min: 0, step: 1, soloOnly: true },
    ],
    recorderDecidesTies: true,
    solo: { kind: "AGAINST_OPPONENT", opponentCategory: "automa", tieWins: true },
  },
}

/**
 * Won on points or outright by a supremacy, and played with or without two Expansions: a part
 * of 7 Wonders Duel's pad. Agora adds a row and a way to win.
 */
export const duelModule: GameModule = {
  id: "9e74ba91-e100-4b33-85d8-2d021f61eb38",
  key: "seven-wonders-duel",
  scorePad: {
    minPlayers: 2,
    maxPlayers: 2,
    expansions: [{ key: "pantheon" }, { key: "agora" }],
    variants: [],
    categories: [
      { key: "civilian", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      { key: "wonders", countsTowardTotal: true, min: 0, step: 1, soloOnly: false },
      {
        key: "senate",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        onlyWhen: { key: "agora", value: "true" },
        soloOnly: false,
      },
    ],
    recorderDecidesTies: false,
    victoryTypes: [
      { key: "civilian", onPoints: true },
      { key: "militarySupremacy", onPoints: false },
      { key: "scientificSupremacy", onPoints: false },
      { key: "politicalSupremacy", onPoints: false, onlyWhen: { key: "agora", value: "true" } },
    ],
  },
}

export const gameModules = [calicoModule, genericModule, duelModule, wyrmspanModule]

export const gameModuleTexts: Record<string, string> = {
  "generic.name": "Generic",
  "generic.category.total": "Score",
  "generic.tie": "Pick who won the tie, or leave the win shared.",
  "generic.invalid.scoreMissing": "Enter a score for every player.",
  "calico.name": "Calico",
  "calico.variant.family": "Family variant",
  "calico.variant.family.hint": "The design goal tiles lie face down and score nothing.",
  "calico.category.designGoals": "Design goals",
  "calico.category.cats": "Cats",
  "calico.category.buttons": "Buttons",
  "calico.tie": "The player with the most cat tokens wins the tie.",
  "seven-wonders-duel.name": "7 Wonders Duel",
  "seven-wonders-duel.expansion.pantheon": "Pantheon",
  "seven-wonders-duel.expansion.agora": "Agora",
  "seven-wonders-duel.category.civilian": "Civilian Buildings (blue)",
  "seven-wonders-duel.category.wonders": "Wonders",
  "seven-wonders-duel.category.senate": "Senate",
  "seven-wonders-duel.victoryType.civilian": "Civilian victory (on points)",
  "seven-wonders-duel.victoryType.militarySupremacy": "Military supremacy",
  "seven-wonders-duel.victoryType.scientificSupremacy": "Scientific supremacy",
  "seven-wonders-duel.victoryType.politicalSupremacy": "Political supremacy",
  "wyrmspan.name": "Wyrmspan",
  "wyrmspan.variant.automaLevel": "Automa level",
  "wyrmspan.variant.automaLevel.1": "Level 1",
  "wyrmspan.variant.automaLevel.2": "Level 2",
  "wyrmspan.variant.automaLevel.3": "Level 3",
  "wyrmspan.variant.ravelMode": "Ravel Mode",
  "wyrmspan.category.dragonGuild": "Dragon Guild",
  "wyrmspan.category.printedDragons": "Printed on dragons",
  "wyrmspan.category.eggs": "Eggs",
  "wyrmspan.category.automa": "The Automa's score",
  "wyrmspan.tie": "The player with the most visible dragons wins the tie.",
}
