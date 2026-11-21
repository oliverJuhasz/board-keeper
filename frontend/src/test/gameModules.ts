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
    playerSetup: [],
    variants: [
      { key: "cooperative", kind: "ON_OFF", defaultValue: "false", soloOnly: false },
      { key: "lowestScoreWins", kind: "ON_OFF", defaultValue: "false", soloOnly: false },
    ],
    categories: [
      { key: "total", countsTowardTotal: true, step: 1, soloOnly: false, afterVictoryOnly: false },
    ],
    recorderDecidesTies: true,
    cooperative: false,
    usualRecordingMode: "POINTS",
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
    playerSetup: [],
    variants: [{ key: "family", kind: "ON_OFF", defaultValue: "false", soloOnly: false }],
    categories: [
      {
        key: "designGoals",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        onlyWhen: { key: "family", value: "false" },
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "cats",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "buttons",
        countsTowardTotal: true,
        min: 0,
        step: 3,
        soloOnly: false,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: true,
    cooperative: false,
    usualRecordingMode: "POINTS",
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
    playerSetup: [],
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
      {
        key: "dragonGuild",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "printedDragons",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "eggs",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "automa",
        countsTowardTotal: false,
        min: 0,
        step: 1,
        soloOnly: true,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: true,
    cooperative: false,
    usualRecordingMode: "POINTS",
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
      {
        key: "civilian",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "wonders",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "senate",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        onlyWhen: { key: "agora", value: "true" },
        soloOnly: false,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: false,
    cooperative: false,
    usualRecordingMode: "POINTS",
    playerSetup: [],
    victoryTypes: [
      { key: "civilian", onPoints: true },
      { key: "militarySupremacy", onPoints: false },
      { key: "scientificSupremacy", onPoints: false },
      { key: "politicalSupremacy", onPoints: false, onlyWhen: { key: "agora", value: "true" } },
    ],
  },
}

/**
 * Each Player has a Wonder, each board once, and plays it on one of its sides: part of 7 Wonders.
 * Petra comes with the Cities expansion.
 */
export const sevenWondersModule: GameModule = {
  id: "1adf0579-480f-4598-b5ea-92416b96b555",
  key: "seven-wonders",
  scorePad: {
    minPlayers: 3,
    maxPlayers: 7,
    expansions: [{ key: "cities" }],
    variants: [],
    categories: [
      {
        key: "wonder",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "military",
        countsTowardTotal: true,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: true,
    cooperative: false,
    usualRecordingMode: "POINTS",
    victoryTypes: [],
    playerSetup: [
      {
        key: "wonder",
        options: ["babylon", "giza", "rhodes", "petra"],
        oncePerPlay: true,
        optionsOnlyWith: { petra: "cities" },
      },
      { key: "side", options: ["day", "night"], oncePerPlay: false, optionsOnlyWith: {} },
    ],
  },
}

/**
 * Won or lost together, with an Adversary whose level is chosen once there is one, and a score
 * worked out from the group's rows: part of Spirit Island.
 */
export const spiritIslandModule: GameModule = {
  id: "d17ecfcd-9564-4576-a5c8-589e82c10cc4",
  key: "spirit-island",
  scorePad: {
    minPlayers: 1,
    maxPlayers: 4,
    expansions: [],
    variants: [
      {
        key: "adversary",
        kind: "CHOICE",
        options: ["none", "england", "sweden"],
        defaultValue: "none",
        soloOnly: false,
      },
      {
        key: "englandLevel",
        kind: "RANGE",
        min: 0,
        max: 6,
        defaultValue: "0",
        soloOnly: false,
        onlyWhen: { key: "adversary", value: "england" },
      },
      {
        key: "swedenLevel",
        kind: "RANGE",
        min: 0,
        max: 6,
        defaultValue: "0",
        soloOnly: false,
        onlyWhen: { key: "adversary", value: "sweden" },
      },
      { key: "thematicMap", kind: "ON_OFF", defaultValue: "false", soloOnly: false },
    ],
    categories: [
      {
        key: "invaderCards",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "dahan",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "blight",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: false,
    cooperative: true,
    usualRecordingMode: "WINNER_ONLY",
    victoryTypes: [],
    playerSetup: [
      {
        key: "spirit",
        options: ["thunderspeaker", "oceansHungryGrasp", "riverSurgesInSunlight"],
        oncePerPlay: true,
        optionsOnlyWith: {},
      },
    ],
  },
}

/**
 * Won or lost together, scored after a victory only, at a difficulty that is Normal unless said
 * otherwise: part of Last Bastion.
 */
export const lastBastionModule: GameModule = {
  id: "e91e79b5-9868-4f88-af26-521b30849941",
  key: "last-bastion",
  scorePad: {
    minPlayers: 1,
    maxPlayers: 4,
    expansions: [],
    variants: [
      {
        key: "difficulty",
        kind: "CHOICE",
        options: ["novice", "normal", "difficult", "heroic"],
        defaultValue: "normal",
        soloOnly: false,
      },
    ],
    categories: [
      {
        key: "lifeTokens",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: true,
      },
      {
        key: "callToArmsTokens",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: true,
      },
      {
        key: "deadHeroes",
        countsTowardTotal: true,
        min: 0,
        max: 3,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: true,
      },
      {
        key: "graspOfEvilTiles",
        countsTowardTotal: true,
        min: 0,
        max: 2,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: true,
      },
    ],
    recorderDecidesTies: false,
    cooperative: true,
    usualRecordingMode: "POINTS",
    victoryTypes: [],
    playerSetup: [
      {
        key: "hero",
        options: ["kazgin", "sindara", "twindalli"],
        oncePerPlay: true,
        optionsOnlyWith: {},
      },
    ],
  },
}

/**
 * For two to five Players, and six with either half of Scoundrels of Skullport, whose Lords come
 * with that half; Skullport adds a row: part of Lords of Waterdeep.
 */
export const lordsOfWaterdeepModule: GameModule = {
  id: "1352e3c2-fb6b-4be5-bc24-762320ed8894",
  key: "lords-of-waterdeep",
  scorePad: {
    minPlayers: 2,
    maxPlayers: 5,
    expansions: [
      { key: "undermountain", maxPlayers: 6 },
      { key: "skullport", maxPlayers: 6 },
    ],
    variants: [],
    categories: [
      {
        key: "scoringTrack",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "gold",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        soloOnly: false,
        afterVictoryOnly: false,
      },
      {
        key: "corruption",
        countsTowardTotal: true,
        max: 0,
        step: 1,
        onlyWhen: { key: "skullport", value: "true" },
        soloOnly: false,
        afterVictoryOnly: false,
      },
    ],
    recorderDecidesTies: true,
    cooperative: false,
    usualRecordingMode: "POINTS",
    victoryTypes: [],
    playerSetup: [
      {
        key: "lord",
        options: ["mirtTheMoneylender", "larissaNeathal", "trobriand", "theXanathar"],
        oncePerPlay: true,
        optionsOnlyWith: { trobriand: "undermountain", theXanathar: "skullport" },
      },
    ],
  },
}

export const gameModules = [
  calicoModule,
  genericModule,
  duelModule,
  sevenWondersModule,
  spiritIslandModule,
  lastBastionModule,
  lordsOfWaterdeepModule,
  wyrmspanModule,
]

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
  "seven-wonders.name": "7 Wonders",
  "seven-wonders.playerSetup.wonder": "Wonder board",
  "seven-wonders.playerSetup.wonder.babylon": "Babylon",
  "seven-wonders.playerSetup.wonder.giza": "Giza",
  "seven-wonders.playerSetup.wonder.rhodes": "Rhodes",
  "seven-wonders.playerSetup.wonder.petra": "Petra",
  "seven-wonders.expansion.cities": "Cities",
  "seven-wonders.playerSetup.side": "Side",
  "seven-wonders.playerSetup.side.day": "Day",
  "seven-wonders.playerSetup.side.night": "Night",
  "seven-wonders.category.wonder": "Wonder",
  "seven-wonders.category.military": "Military",
  "seven-wonders.tie": "The player with the most coins wins the tie.",
  "spirit-island.name": "Spirit Island",
  "spirit-island.variant.adversary": "Adversary",
  "spirit-island.variant.adversary.none": "No adversary",
  "spirit-island.variant.adversary.england": "England",
  "spirit-island.variant.adversary.sweden": "Sweden",
  "spirit-island.variant.englandLevel": "Level of England",
  "spirit-island.variant.englandLevel.0": "Base level",
  "spirit-island.variant.swedenLevel": "Level of Sweden",
  "spirit-island.variant.swedenLevel.0": "Base level",
  "spirit-island.variant.thematicMap": "Thematic map",
  "spirit-island.category.invaderCards": "Invader cards",
  "spirit-island.category.invaderCards.won": "Invader cards left in the deck",
  "spirit-island.category.invaderCards.lost": "Invader cards no longer in the deck",
  "spirit-island.category.dahan": "Living Dahan",
  "spirit-island.category.blight": "Blight on the island",
  "spirit-island.playerSetup.spirit": "Spirit",
  "spirit-island.playerSetup.spirit.thunderspeaker": "Thunderspeaker",
  "spirit-island.playerSetup.spirit.oceansHungryGrasp": "Ocean's Hungry Grasp",
  "spirit-island.playerSetup.spirit.riverSurgesInSunlight": "River Surges in Sunlight",
  "last-bastion.name": "Last Bastion",
  "last-bastion.variant.difficulty": "Difficulty",
  "last-bastion.variant.difficulty.hint": "A victory scores 5, 10, 15 or 20 points, by difficulty.",
  "last-bastion.variant.difficulty.novice": "Novice",
  "last-bastion.variant.difficulty.normal": "Normal",
  "last-bastion.variant.difficulty.difficult": "Difficult",
  "last-bastion.variant.difficulty.heroic": "Heroic",
  "last-bastion.category.lifeTokens": "Life tokens",
  "last-bastion.category.callToArmsTokens": "Call to Arms tokens",
  "last-bastion.category.deadHeroes": "Dead heroes",
  "last-bastion.category.graspOfEvilTiles": "Bastion tiles in the Grasp of Evil",
  "last-bastion.playerSetup.hero": "Hero",
  "last-bastion.playerSetup.hero.kazgin": "Kazgin",
  "last-bastion.playerSetup.hero.sindara": "Sindara",
  "last-bastion.playerSetup.hero.twindalli": "Twindalli",
  "lords-of-waterdeep.name": "Lords of Waterdeep",
  "lords-of-waterdeep.expansion.undermountain": "Scoundrels of Skullport: Undermountain",
  "lords-of-waterdeep.expansion.skullport": "Scoundrels of Skullport: Skullport",
  "lords-of-waterdeep.category.scoringTrack": "Victory Points on the scoring track",
  "lords-of-waterdeep.category.gold": "Gold in the Tavern (1 per 2)",
  "lords-of-waterdeep.category.corruption": "Corruption (tokens × the farthest empty space)",
  "lords-of-waterdeep.tie": "The player with the most Gold in their Tavern wins the tie.",
  "lords-of-waterdeep.playerSetup.lord": "Lord of Waterdeep",
  "lords-of-waterdeep.playerSetup.lord.mirtTheMoneylender": "Mirt the Moneylender",
  "lords-of-waterdeep.playerSetup.lord.larissaNeathal": "Larissa Neathal",
  "lords-of-waterdeep.playerSetup.lord.trobriand": "Trobriand",
  "lords-of-waterdeep.playerSetup.lord.theXanathar": "The Xanathar",
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
