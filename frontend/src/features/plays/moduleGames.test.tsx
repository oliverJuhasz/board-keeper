import { screen, waitFor, within } from "@testing-library/react"
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
      markedWinner: false,
      total: 72,
      placing: 1,
      won: true,
    },
    {
      player: annaPlayer,
      scores: { designGoals: 21, cats: 19, buttons: 18 },
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
  await userEvent.type(await screen.findByLabelText(`Design goals of ${name}`), designGoals)
  await userEvent.type(screen.getByLabelText(`Cats of ${name}`), cats)
  await userEvent.type(screen.getByLabelText(`Buttons of ${name}`), buttons)
}

/** What a row of the pad says, cell by cell. */
function padRow(name: string) {
  const pad = screen.getByRole("table", { name: "Score pad" })
  const row = within(pad).getByRole("row", { name: new RegExp(`^${name}`) })
  return within(row)
    .getAllByRole("cell")
    .map((cell) => cell.textContent)
}

async function startCalico() {
  signedInAs(bence)
  gamesAre([azul, calico])
  playCanBeEntered()
  const asked = playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${calico.id}`)
  await screen.findByRole("heading", { name: "Record a play" })
  return { asked, sent }
}

test("a game with its own module is listed under the module's name and can't be changed", async () => {
  signedInAs(bence)
  gamesAre([azul, calico])
  renderApp("/")

  const row = await screen.findByRole("row", { name: /Calico/ })
  expect(within(row).getByText("Its own score pad")).toBeInTheDocument()
  expect(within(row).getByRole("link", { name: "Record a play of Calico" })).toBeInTheDocument()
  // Its menu leads to its statistics, and has nothing to edit or remove.
  await userEvent.click(within(row).getByRole("button", { name: "Actions for Calico" }))
  expect(await screen.findByRole("menuitem", { name: "Statistics" })).toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Edit" })).not.toBeInTheDocument()
  expect(screen.queryByRole("menuitem", { name: "Remove" })).not.toBeInTheDocument()
})

test("the form is the game's score pad, and the server adds it up", async () => {
  const { sent } = await startCalico()

  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")
  await fillPad("Bence", "28", "23", "21")
  // Nothing is worked out until the pad is complete.
  expect(padRow("Total")).toEqual(["–", "–"])
  await fillPad("Anna", "21", "19", "18")

  await waitFor(() => expect(padRow("Total")).toEqual(["72", "58"]))
  expect(padRow("Placing")).toEqual(["1", "2"])
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "Calico" })
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
        },
        {
          playerId: annaPlayer.id,
          scores: { designGoals: 21, cats: 19, buttons: 18 },
          winner: false,
        },
      ],
    },
  ])
})

test("a variant of the game changes the pad", async () => {
  const { sent } = await startCalico()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")
  expect(screen.getByLabelText("Design goals of Anna")).toBeInTheDocument()

  // The module explains its variant in its own words.
  await userEvent.click(screen.getByRole("checkbox", { name: "Family variant" }))
  expect(
    screen.getByText("The design goal tiles lie face down and score nothing."),
  ).toBeInTheDocument()

  expect(screen.queryByLabelText("Design goals of Anna")).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText("Cats of Bence"), "23")
  await userEvent.type(screen.getByLabelText("Buttons of Bence"), "21")
  await userEvent.type(screen.getByLabelText("Cats of Anna"), "25")
  await userEvent.type(screen.getByLabelText("Buttons of Anna"), "24")
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "Calico" })
  expect(sent[0]).toMatchObject({
    setup: { family: "true" },
    players: [{ scores: { cats: 23, buttons: 21 } }, { scores: { cats: 25, buttons: 24 } }],
  })
})

test("each row of the pad has its own limits", async () => {
  const { asked, sent } = await startCalico()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")
  await fillPad("Bence", "28", "23", "20")
  await fillPad("Anna", "21", "19", "18")
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  // A button is worth 3 points, so 20 can't be right. The server was never asked.
  expect(await screen.findByText("Buttons of Bence: Enter a multiple of 3.")).toBeInTheDocument()
  expect(asked).toEqual([])
  expect(sent).toEqual([])

  await userEvent.clear(screen.getByLabelText("Buttons of Bence"))
  await userEvent.type(screen.getByLabelText("Buttons of Bence"), "21")
  expect(screen.queryByText(/Enter a multiple of 3/)).not.toBeInTheDocument()
})

test("a game says how many can play", async () => {
  await startCalico()
  // Calico can be played alone, so the recorder on their own is enough. Nobody isn't.
  await userEvent.click(screen.getByRole("button", { name: "Remove Bence" }))

  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  expect(await screen.findByText("Add at least one player.")).toBeInTheDocument()

  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")
  expect(screen.queryByText("Add at least one player.")).not.toBeInTheDocument()
})

test("a tie is explained in the game's own words", async () => {
  const { sent } = await startCalico()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")
  await fillPad("Bence", "28", "23", "21")
  await fillPad("Anna", "30", "21", "21")

  // 72 each. Calico decides that by cat tokens, which are not on the pad.
  expect(await screen.findByText("Tie for first place")).toBeInTheDocument()
  expect(screen.getByText("The player with the most cat tokens wins the tie.")).toBeInTheDocument()
  await userEvent.click(screen.getByRole("radio", { name: "Anna" }))
  // The pad shows the Placings as they will be recorded.
  expect(padRow("Placing")).toEqual(["2", "1"])
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "Calico" })
  expect(sent[0].players.map((player) => player.winner)).toEqual([false, true])
})

test("only the winner can be recorded for a game with its own module, too", async () => {
  const { asked, sent } = await startCalico()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Dora")

  await userEvent.click(screen.getByRole("button", { name: "Change" }))
  await userEvent.click(screen.getByRole("radio", { name: "Winner only" }))
  expect(screen.queryByRole("table", { name: "Score pad" })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("checkbox", { name: "Dora won" }))
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  await screen.findByRole("heading", { name: "Calico" })
  expect(sent[0]).toMatchObject({
    recordingMode: "WINNER_ONLY",
    setup: { family: "false" },
    players: [
      { playerId: bencePlayer.id, winner: false },
      { playerId: doraPlayer.id, winner: true },
    ],
  })
  expect(asked).toEqual([])
})

test("a play's page shows every row of the pad", async () => {
  signedInAs(bence)
  playIs(calicoPlay)
  renderApp(`/plays/${calicoPlay.id}`)

  expect(await screen.findByRole("heading", { name: "Calico" })).toBeInTheDocument()
  const results = within(screen.getByRole("table", { name: "Results" })).getAllByRole("row")
  expect(results.map((row) => row.textContent)).toEqual([
    "PlacingPlayerTotal",
    "1BenceWinner72",
    "2Anna58",
  ])
  // The pad as it was filled in: a row for each category, a column for each player.
  const pad = within(screen.getByRole("table", { name: "Score pad" })).getAllByRole("row")
  expect(pad.map((row) => row.textContent)).toEqual([
    "BenceAnna",
    "Design goals2821",
    "Cats2319",
    "Buttons2118",
    "Total7258",
  ])
  expect(screen.getByText(/^Points\. Recorded by Bence/)).toBeInTheDocument()
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

  expect(await screen.findByText(/^Points, Family variant\. Recorded by/)).toBeInTheDocument()
  expect(screen.getByRole("rowheader", { name: "Cats" })).toBeInTheDocument()
  expect(screen.queryByRole("rowheader", { name: "Design goals" })).not.toBeInTheDocument()
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

  expect(await screen.findByLabelText("Score of Bence")).toHaveValue(72)
  expect(screen.queryByRole("table", { name: "Score pad" })).not.toBeInTheDocument()
})
