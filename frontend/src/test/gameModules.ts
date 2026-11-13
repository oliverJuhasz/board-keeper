import type { GameModule } from "@/api/client"

// The Game Modules of the pretend backend, as the real one describes them, and their English
// texts. Kept apart from the other fixtures because the pretend server itself needs them.

export const genericModule: GameModule = {
  id: "38b119d2-8f9f-4e20-8068-cba305c06cdd",
  key: "generic",
  scorePad: {
    minPlayers: 1,
    variants: [
      { key: "cooperative", kind: "ON_OFF", defaultValue: "false" },
      { key: "lowestScoreWins", kind: "ON_OFF", defaultValue: "false" },
    ],
    categories: [{ key: "total", countsTowardTotal: true, step: 1 }],
    recorderDecidesTies: true,
  },
}

export const calicoModule: GameModule = {
  id: "d392b4e5-a531-44c4-85c7-369783d9be5c",
  key: "calico",
  scorePad: {
    minPlayers: 2,
    maxPlayers: 4,
    variants: [{ key: "family", kind: "ON_OFF", defaultValue: "false" }],
    categories: [
      {
        key: "designGoals",
        countsTowardTotal: true,
        min: 0,
        step: 1,
        onlyWhen: { variant: "family", value: "false" },
      },
      { key: "cats", countsTowardTotal: true, min: 0, step: 1 },
      { key: "buttons", countsTowardTotal: true, min: 0, step: 3 },
    ],
    recorderDecidesTies: true,
  },
}

export const gameModules = [calicoModule, genericModule]

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
}
