import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import { todayIso } from "@/lib/dates"
import {
  aNewPlayId,
  annaPlayer,
  azul,
  bence,
  bencePlayer,
  calico,
  doraPlayer,
  gamesAre,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  signedInAs,
} from "@/test/api"
import { calicoModule, genericModule } from "@/test/gameModules"
import { findScoreField, padFoot, playerRow, queryScoreField, scoreField } from "@/test/playForm"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// Plays of a Game that has a Game Module of its own, with Calico as the example. Its form is
// drawn from the module's score pad: a pad with a row for design goals, cats and buttons.

/** A Play of Calico as the server would answer it: Bence 72, Anna 58. */
const calicoPlay: Play = {
  id: "0193a000-0000-7000-8000-0000000000f4",
  game: { id: calico.id, name: calico.name, moduleKey: "calico" },
  gameModuleId: calicoModule.id,
  playedOn: "2026-03-14",
  recordingMode: "POINTS",
  setup: { family: "false" },
  cooperative: false,
  results: [
    {
      player: bencePlayer,
      scores: { designGoals: 28, cats: 23, buttons: 21 },
      setup: {},
      markedWinner: false,
      total: 72,
      placing: 1,
      won: true,
    },
    {
      player: annaPlayer,
      scores: { designGoals: 21, cats: 19, buttons: 18 },
      setup: {},
      markedWinner: false,
      total: 58,
      placing: 2,
      won: false,
    },
  ],
  recordedBy: bencePlayer,
  recordedAt: "2026-03-14T12:00:00Z",
  canChange: true,
}

function playsCanBeRecorded() {
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(calicoPlay)
    }),
  )
  playIs(calicoPlay)
  return sent
}

/** Types a Player's column of the pad: design goals, cats, buttons. */
async function fillPad(name: string, designGoals: string, cats: string, buttons: string) {
  await userEvent.type(await findScoreField(name, "designGoals"), designGoals)
  await userEvent.type(scoreField(name, "cats"), cats)
  await userEvent.type(scoreField(name, "buttons"), buttons)
}

async function startCalico() {
  signedInAs(bence)
  gamesAre([azul, calico])
  playCanBeEntered()
  const asked = playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)
  await page.findByTestIdWith("page-title", "Record a play")
  return { asked, sent }
}

test("a game with its own module is listed under the module's name and can't be changed", async () => {
  signedInAs(bence)
  gamesAre([azul, calico])
  renderApp("/")

  const row = inside(await page.findByTestIdWith("game-row", "Calico"))
  expect(row.getByTestId("recorded-as")).toHaveTextContent("Its own score pad")
  expect(row.getByTestId("record-play")).toHaveTextContent("Calico")
  // Its menu leads to its statistics, and has nothing to edit or remove.
  await userEvent.click(row.getByTestId("game-actions"))
  expect(await page.findByTestId("game-statistics")).toBeInTheDocument()
  expect(page.queryByTestId("edit-game")).not.toBeInTheDocument()
  expect(page.queryByTestId("remove-game")).not.toBeInTheDocument()
})

test("the form is the game's score pad, and the server adds it up", async () => {
  const { sent } = await startCalico()

  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  await fillPad("Bence", "28", "23", "21")
  // Nothing is worked out until the pad is complete.
  expect(padFoot("total")).toEqual(["–", "–"])
  await fillPad("Anna", "21", "19", "18")

  await waitFor(() => expect(padFoot("total")).toEqual(["72", "58"]))
  expect(padFoot("placing")).toEqual(["1", "2"])
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Calico")
  expect(sent).toEqual([
    {
      id: aNewPlayId,
      gameId: calico.id,
      playedOn: todayIso(),
      recordingMode: "POINTS",
      setup: { family: "false" },
      players: [
        {
          playerId: bencePlayer.id,
          scores: { designGoals: 28, cats: 23, buttons: 21 },
          winner: false,
          setup: {},
        },
        {
          playerId: annaPlayer.id,
          scores: { designGoals: 21, cats: 19, buttons: 18 },
          winner: false,
          setup: {},
        },
      ],
    },
  ])
})

test("a variant of the game changes the pad", async () => {
  const { sent } = await startCalico()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  expect(scoreField("Anna", "designGoals")).toBeInTheDocument()

  // The module explains its variant in its own words.
  await userEvent.click(page.getByTestId("variant-family"))
  expect(page.getByTestId("variant-family-hint")).toHaveTextContent(
    "The design goal tiles lie face down and score nothing.",
  )

  expect(queryScoreField("Anna", "designGoals")).not.toBeInTheDocument()
  await userEvent.type(scoreField("Bence", "cats"), "23")
  await userEvent.type(scoreField("Bence", "buttons"), "21")
  await userEvent.type(scoreField("Anna", "cats"), "25")
  await userEvent.type(scoreField("Anna", "buttons"), "24")
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Calico")
  expect(sent[0]).toMatchObject({
    setup: { family: "true" },
    players: [{ scores: { cats: 23, buttons: 21 } }, { scores: { cats: 25, buttons: 24 } }],
  })
})

test("each row of the pad has its own limits", async () => {
  const { asked, sent } = await startCalico()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  await fillPad("Bence", "28", "23", "20")
  await fillPad("Anna", "21", "19", "18")
  await userEvent.click(page.getByTestId("save-play"))

  // A button is worth 3 points, so 20 can't be right. The server was never asked.
  expect(
    await page.findByTestIdWith("field-error", "Buttons of Bence: Enter a multiple of 3."),
  ).toBeInTheDocument()
  expect(asked).toEqual([])
  expect(sent).toEqual([])

  await userEvent.clear(scoreField("Bence", "buttons"))
  await userEvent.type(scoreField("Bence", "buttons"), "21")
  expect(page.queryByTestIdWith("field-error", /Enter a multiple of 3/)).not.toBeInTheDocument()
})

test("a game says how many can play", async () => {
  await startCalico()
  // Calico can be played alone, so the recorder on their own is enough. Nobody isn't.
  await userEvent.click(playerRow("Bence").getByTestId("remove-player"))

  await userEvent.click(page.getByTestId("save-play"))
  expect(await page.findByTestIdWith("field-error", "Add at least one player.")).toBeInTheDocument()

  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  expect(page.queryByTestIdWith("field-error", "Add at least one player.")).not.toBeInTheDocument()
})

test("focus stays on the list of players until the game is full, then moves on to the pad", async () => {
  await startCalico()
  const eszter = { id: "0193a000-0000-7000-8000-0000000000b9", name: "Eszter" }
  server.use(http.post("*/api/players", () => HttpResponse.json(eszter)))

  // Someone else may still join, so the pad can wait.
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  expect(scoreField("Anna", "designGoals")).toBeInTheDocument()
  expect(page.getByTestId("players-to-add")).toHaveFocus()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Dora")
  expect(page.getByTestId("players-to-add")).toHaveFocus()

  // Calico is for at most four: the last one fills the game, so their column is next.
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Add a new player…")
  await page.findByTestId("player-name-dialog")
  await userEvent.type(page.getByTestId("player-name"), "Eszter{Enter}")
  expect(await page.findByTestId("players-full")).toHaveTextContent(
    "This game is for at most 4 players.",
  )
  await waitFor(() => expect(scoreField("Eszter", "designGoals")).toHaveFocus())
})

test("a tie is explained in the game's own words", async () => {
  const { sent } = await startCalico()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")
  await fillPad("Bence", "28", "23", "21")
  await fillPad("Anna", "30", "21", "21")

  // 72 each. Calico decides that by cat tokens, which are not on the pad.
  expect(await page.findByTestIdWith("tie-winner", "Tie for first place")).toBeInTheDocument()
  expect(page.getByTestId("tie-explanation")).toHaveTextContent(
    "The player with the most cat tokens wins the tie.",
  )
  await userEvent.click(page.getByTestId("tie-winner-Anna"))
  // The pad shows the Placings as they will be recorded.
  expect(padFoot("placing")).toEqual(["2", "1"])
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Calico")
  expect(sent[0].players.map((player) => player.winner)).toEqual([false, true])
})

test("only the winner can be recorded for a game with its own module, too", async () => {
  const { asked, sent } = await startCalico()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Dora")

  await userEvent.click(page.getByTestId("change-recording"))
  await userEvent.click(page.getByTestId("recording-mode-WINNER_ONLY"))
  expect(page.queryByTestId("score-pad")).not.toBeInTheDocument()
  await userEvent.click(playerRow("Dora").getByTestId("won"))
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Calico")
  expect(sent[0]).toMatchObject({
    recordingMode: "WINNER_ONLY",
    setup: { family: "false" },
    players: [
      { playerId: bencePlayer.id, winner: false, setup: {} },
      { playerId: doraPlayer.id, winner: true, setup: {} },
    ],
  })
  expect(asked).toEqual([])
})

test("a play's page shows every row of the pad", async () => {
  signedInAs(bence)
  playIs(calicoPlay)
  renderApp(`/plays/${calicoPlay.id}`)

  expect(await page.findByTestIdWith("page-title", "Calico")).toBeInTheDocument()
  expect(page.getByTestId("results-header")).toHaveTextContent("PlacingPlayerTotal")
  expect(page.getAllByTestId("result-row").map((row) => row.textContent)).toEqual([
    "1BenceWinner72",
    "2Anna58",
  ])
  // The pad as it was filled in: a row for each category, a column for each player.
  expect(page.getByTestId("pad-players")).toHaveTextContent("BenceAnna")
  expect(page.getAllByTestId("pad-row").map((row) => row.textContent)).toEqual([
    "Design goals2821",
    "Cats2319",
    "Buttons2118",
  ])
  expect(page.getByTestId("pad-total")).toHaveTextContent("Total7258")
  expect(page.getByTestId("recorded-by")).toHaveTextContent(/^Points\. Recorded by Bence/)
})

test("a play's page names the variants it was played with", async () => {
  signedInAs(bence)
  const family: Play = {
    ...calicoPlay,
    setup: { family: "true" },
    results: calicoPlay.results.map((result) => ({
      ...result,
      scores: { cats: result.scores.cats, buttons: result.scores.buttons },
    })),
  }
  playIs(family)
  renderApp(`/plays/${family.id}`)

  expect(await page.findByTestId("recorded-by")).toHaveTextContent(
    /^Points, Family variant\. Recorded by/,
  )
  expect(page.getByTestIdWith("pad-row", "Cats")).toBeInTheDocument()
  expect(page.queryByTestIdWith("pad-row", "Design goals")).not.toBeInTheDocument()
})

test("a play from before the module arrived is edited as it was recorded", async () => {
  signedInAs(bence)
  // Calico was once added by hand, and this play was recorded with totals only.
  const old: Play = {
    ...calicoPlay,
    gameModuleId: genericModule.id,
    setup: { cooperative: "false", lowestScoreWins: "false" },
    results: calicoPlay.results.map((result) => ({
      ...result,
      scores: { total: result.total ?? 0 },
    })),
  }
  playIs(old)
  playCanBeEntered()
  playsCanBePreviewed()
  renderApp(`/plays/${old.id}/edit`)

  expect(await findScoreField("Bence")).toHaveValue(72)
  expect(page.queryByTestId("score-pad")).not.toBeInTheDocument()
})
