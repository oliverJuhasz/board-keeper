import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play, PlayPreview, PlayPreviewRequest } from "@/api/client"
import {
  bence,
  bencePlayer,
  calico,
  gamesAre,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  signedInAs,
  wyrmspan,
} from "@/test/api"
import { calicoModule, gameModuleTexts, genericModule, wyrmspanModule } from "@/test/gameModules"
import { findScoreField, padFoot, playerRow, queryScoreField, scoreField } from "@/test/playForm"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// Solo Plays: a Play of one. Calico is played alone for a score, which nobody wins or loses.
// Wyrmspan is played against the Automa, whose score is a row of the pad in a Solo Play only.

/** A recorded Play of one, as the server would answer it. */
function soloPlay(overrides: Partial<Play>): Play {
  return {
    id: "0193a000-0000-7000-8000-0000000000f7",
    game: { id: wyrmspan.id, name: wyrmspan.name, moduleKey: "wyrmspan" },
    gameModuleId: wyrmspanModule.id,
    playedOn: "2026-03-14",
    recordingMode: "POINTS",
    setup: { automaLevel: "2", ravelMode: "true" },
    cooperative: false,
    results: [
      {
        player: bencePlayer,
        scores: { dragonGuild: 12, printedDragons: 40, eggs: 9, automa: 55 },
        setup: {},
        markedWinner: false,
        total: 61,
        won: true,
      },
    ],
    recordedBy: bencePlayer,
    recordedAt: "2026-03-14T12:00:00Z",
    canChange: true,
    ...overrides,
  }
}

function playsCanBeRecorded(answer: Play) {
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(answer)
    }),
  )
  playIs(answer)
  return sent
}

/**
 * Answers how a Solo Play against the Automa would end, as the Wyrmspan module would: the
 * Automa's row doesn't count, and a tie with it is a win.
 */
function automaCanBePreviewed() {
  server.use(
    http.post("*/api/plays/preview", async ({ request }) => {
      const body = (await request.json()) as PlayPreviewRequest
      const preview: PlayPreview = {
        cooperative: false,
        results: body.players.map((player) => {
          const { automa = 0, ...counted } = player.scores ?? {}
          const total = Object.values(counted).reduce((sum, value) => sum + value, 0)
          return { playerId: player.playerId, total, won: total >= automa }
        }),
        tieForRecorder: [],
      }
      return HttpResponse.json(preview)
    }),
  )
}

async function fillWyrmspan(name: string, dragonGuild: string, printed: string, eggs: string) {
  await userEvent.type(await findScoreField(name, "dragonGuild"), dragonGuild)
  await userEvent.type(scoreField(name, "printedDragons"), printed)
  await userEvent.type(scoreField(name, "eggs"), eggs)
}

test("a solo play for score only has a total, but no placing and no winner", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded(
    soloPlay({
      game: { id: calico.id, name: "Calico", moduleKey: "calico" },
      gameModuleId: calicoModule.id,
      setup: { family: "false" },
      results: [
        {
          player: bencePlayer,
          scores: { designGoals: 28, cats: 23, buttons: 21 },
          setup: {},
          markedWinner: false,
          total: 72,
        },
      ],
    }),
  )
  renderApp(`/plays/new?game=${calico.id}`)

  // Only the recorder is in the play.
  await userEvent.type(await findScoreField("Bence", "designGoals"), "28")
  await userEvent.type(scoreField("Bence", "cats"), "23")
  await userEvent.type(scoreField("Bence", "buttons"), "21")

  await waitFor(() => expect(padFoot("total")).toEqual(["72"]))
  expect(padFoot("placing")).toEqual([])
  expect(padFoot("solo-result")).toEqual([])
  await userEvent.click(page.getByTestId("save-play"))

  // Its page has neither a Placing nor a badge.
  await page.findByTestIdWith("page-title", "Calico")
  expect(page.getByTestId("results-header")).toHaveTextContent(/^PlayerTotal$/)
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual(["Bence72"])
  expect(sent[0]).toMatchObject({
    setup: { family: "false" },
    players: [
      {
        playerId: bencePlayer.id,
        scores: { designGoals: 28, cats: 23, buttons: 21 },
        winner: false,
      },
    ],
  })
})

test("a solo play for score only can't be recorded with only the winner", async () => {
  signedInAs(bence)
  gamesAre([calico])
  playCanBeEntered()
  renderApp(`/plays/new?game=${calico.id}`)

  await userEvent.click(await page.findByTestId("change-recording"))
  await userEvent.click(page.getByTestId("recording-mode-WINNER_ONLY"))
  expect(page.queryByTestId("won")).not.toBeInTheDocument()
  await userEvent.click(page.getByTestId("save-play"))

  expect(
    await page.findByTestIdWith(
      "field-error",
      "A solo play of this game is recorded with its scores. Choose Points.",
    ),
  ).toBeInTheDocument()
})

test("a solo play against the Automa records its level and its score, and is won or lost", async () => {
  signedInAs(bence)
  gamesAre([wyrmspan])
  playCanBeEntered()
  automaCanBePreviewed()
  const sent = playsCanBeRecorded(soloPlay({}))
  renderApp(`/plays/new?game=${wyrmspan.id}`)

  // The Automa's level and Ravel Mode belong to a Solo Play.
  await userEvent.click(await page.findByTestId("variant-automaLevel-2"))
  await userEvent.click(page.getByTestId("variant-ravelMode"))
  await fillWyrmspan("Bence", "12", "40", "9")
  await userEvent.type(scoreField("Bence", "automa"), "61")

  // A tie with the Automa is won.
  await waitFor(() => expect(padFoot("total")).toEqual(["61"]))
  expect(padFoot("solo-result")).toEqual(["Won"])
  await userEvent.clear(scoreField("Bence", "automa"))
  await userEvent.type(scoreField("Bence", "automa"), "70")
  await waitFor(() => expect(padFoot("solo-result")).toEqual(["Lost"]))

  await userEvent.click(page.getByTestId("save-play"))
  await page.findByTestIdWith("page-title", "Wyrmspan")
  expect(sent[0]).toMatchObject({
    setup: { automaLevel: "2", ravelMode: "true" },
    players: [
      {
        playerId: bencePlayer.id,
        scores: { dragonGuild: 12, printedDragons: 40, eggs: 9, automa: 70 },
        winner: false,
      },
    ],
  })
})

test("a Variant with many options is picked from a drop-down", async () => {
  // As if the Automa had six levels, as Hadara's NPC has twelve.
  const levels = ["1", "2", "3", "4", "5", "6"]
  const manyLevels = structuredClone(wyrmspanModule)
  manyLevels.scorePad.variants[0].options = levels
  server.use(
    http.get("*/api/game-modules", () =>
      HttpResponse.json([calicoModule, genericModule, manyLevels]),
    ),
    http.get("*/api/game-modules/texts/:language", () =>
      HttpResponse.json({
        ...gameModuleTexts,
        ...Object.fromEntries(
          levels.map((level) => [`wyrmspan.variant.automaLevel.${level}`, `Level ${level}`]),
        ),
      }),
    ),
  )
  signedInAs(bence)
  gamesAre([wyrmspan])
  playCanBeEntered()
  automaCanBePreviewed()
  const sent = playsCanBeRecorded(soloPlay({}))
  renderApp(`/plays/new?game=${wyrmspan.id}`)

  const level = await page.findByTestId("variant-automaLevel")
  expect(page.queryByTestId("variant-automaLevel-1")).not.toBeInTheDocument()
  expect(
    Array.from((level as HTMLSelectElement).options).map((option) => option.textContent),
  ).toEqual(levels.map((value) => `Level ${value}`))
  await userEvent.selectOptions(level, "Level 5")
  await fillWyrmspan("Bence", "12", "40", "9")
  await userEvent.type(scoreField("Bence", "automa"), "61")
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Wyrmspan")
  expect(sent[0]).toMatchObject({ setup: { automaLevel: "5" } })
})

test("the Automa is not part of a play with more players", async () => {
  signedInAs(bence)
  gamesAre([wyrmspan])
  playCanBeEntered()
  playsCanBePreviewed()
  const sent = playsCanBeRecorded(soloPlay({}))
  renderApp(`/plays/new?game=${wyrmspan.id}`)

  // The Players come first, so adding one takes away choices further down the form, not above.
  const levelOne = await page.findByTestId("variant-automaLevel-1")
  expect(page.getByTestId("players-to-add").compareDocumentPosition(levelOne)).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  )
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")

  expect(page.queryByTestId("variant-automaLevel-1")).not.toBeInTheDocument()
  expect(page.queryByTestId("variant-ravelMode")).not.toBeInTheDocument()
  expect(queryScoreField("Bence", "automa")).not.toBeInTheDocument()
  await fillWyrmspan("Bence", "12", "40", "9")
  await fillWyrmspan("Anna", "5", "30", "2")
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Wyrmspan")
  expect(sent[0].setup).toEqual({})
  expect(sent[0].players.map((player) => Object.keys(player.scores ?? {}))).toEqual([
    ["dragonGuild", "printedDragons", "eggs"],
    ["dragonGuild", "printedDragons", "eggs"],
  ])
})

test("a solo play against the Automa can be recorded as won or lost only", async () => {
  signedInAs(bence)
  gamesAre([wyrmspan])
  playCanBeEntered()
  const sent = playsCanBeRecorded(soloPlay({}))
  renderApp(`/plays/new?game=${wyrmspan.id}`)

  await userEvent.click(await page.findByTestId("change-recording"))
  // Against an opponent, a Solo Play is won or lost.
  expect(page.getByTestId("recording-mode")).toHaveTextContent("Won or lost only")
  await userEvent.click(page.getByTestId("recording-mode-WINNER_ONLY"))
  // Lost: "won" stays unticked, and that is a complete answer.
  expect(playerRow("Bence").getByTestId("won")).not.toBeChecked()
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Wyrmspan")
  expect(sent[0]).toMatchObject({
    recordingMode: "WINNER_ONLY",
    setup: { automaLevel: "1", ravelMode: "false" },
    players: [{ playerId: bencePlayer.id, winner: false }],
  })
})

test("a solo play's page says whether it was won, and against which level", async () => {
  signedInAs(bence)
  playIs(soloPlay({}))
  renderApp(`/plays/${soloPlay({}).id}`)

  expect(await page.findByTestId("results-header")).toHaveTextContent(/^PlayerTotal$/)
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual(["BenceWon61"])
  expect(page.getByTestId("recorded-by")).toHaveTextContent(
    /^Points, Automa level: Level 2, Ravel Mode\./,
  )
  expect(page.getByTestIdWith("pad-row", "The Automa's score")).toBeInTheDocument()
})

test("a play of more players doesn't name the Automa's level", async () => {
  signedInAs(bence)
  const play = soloPlay({
    setup: {},
    results: [
      {
        ...soloPlay({}).results[0],
        scores: { dragonGuild: 12, printedDragons: 40, eggs: 9 },
        placing: 1,
      },
      {
        player: { id: "0193a000-0000-7000-8000-0000000000b1", name: "Anna" },
        scores: { dragonGuild: 5, printedDragons: 30, eggs: 2 },
        setup: {},
        markedWinner: false,
        total: 37,
        placing: 2,
        won: false,
      },
    ],
  })
  playIs(play)
  renderApp(`/plays/${play.id}`)

  expect(await page.findByTestId("recorded-by")).toHaveTextContent(/^Points\. Recorded by/)
})
