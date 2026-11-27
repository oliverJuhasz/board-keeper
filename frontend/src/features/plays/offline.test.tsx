import { onlineManager } from "@tanstack/react-query"
import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { afterEach, beforeEach, expect, test } from "vitest"
import type { NewPlay } from "@/api/client"
import {
  aNewPlayId,
  anna,
  azul,
  azulPlay,
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  problem,
  signedInAs,
} from "@/test/api"
import { genericModule } from "@/test/gameModules"
import { findScoreField, scoreField } from "@/test/playForm"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"
import { keepWaiting, type WaitingPlay } from "./waitingPlays"

// Recording Plays without a connection: they wait on the device, and are sent once Board
// Keeper can be reached. TanStack Query's onlineManager says whether the device is online.

/** A Play of Azul that Bence recorded on this device: Bence 48, Dora 61. */
function waitingPlay(overrides: Partial<WaitingPlay> = {}): WaitingPlay {
  return {
    accountId: bence.id,
    gameModuleId: genericModule.id,
    savedAt: "2026-03-14T20:00:00Z",
    play: {
      id: "019ceb9f-fa00-7000-8000-000000000001",
      gameId: azul.id,
      playedOn: "2026-03-14",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: bencePlayer.id, scores: { total: 48 }, winner: false },
        { playerId: doraPlayer.id, scores: { total: 61 }, winner: false },
      ],
    },
    ...overrides,
  }
}

/** Records every Play sent, and answers with azulPlay. */
function playsCanBeRecorded() {
  const sent: NewPlay[] = []
  server.use(
    http.post("*/api/plays", async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      return HttpResponse.json(azulPlay)
    }),
  )
  playIs(azulPlay)
  return sent
}

/** The start page's list of Plays not sent yet. */
async function waitingList() {
  return inside(await page.findByTestId("waiting-plays"))
}

beforeEach(() => {
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
})

afterEach(() => onlineManager.setOnline(true))

test("without a connection, a play is kept on the device and listed on the start page", async () => {
  playsCanBePreviewed()
  renderApp(`/plays/new?game=${azul.id}`)
  await findScoreField("Bence")
  onlineManager.setOnline(false)

  expect(await page.findByTestId("kept-on-device")).toHaveTextContent(
    "You're offline. This play will be kept on this device and sent once there's a connection. Its totals and placings are worked out then.",
  )
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Dora")
  await userEvent.type(scoreField("Bence"), "48")
  await userEvent.type(scoreField("Dora"), "61")
  await userEvent.click(page.getByTestId("save-play"))

  const list = await waitingList()
  expect(list.getByTestId("waiting-play-game")).toHaveTextContent(/^Azul ·/)
  expect(list.getByTestId("waiting-play-players")).toHaveTextContent("Bence, Dora")
  expect(list.getByTestId("waiting-play-status")).toHaveTextContent("Waiting for a connection")
  expect(page.getByTestId("offline")).toHaveTextContent(
    /You're offline\. Plays you record are kept/,
  )
  const kept = JSON.parse(localStorage.getItem("boardkeeper.waitingPlays") ?? "[]") as WaitingPlay[]
  expect(kept).toEqual([
    expect.objectContaining({
      accountId: bence.id,
      play: expect.objectContaining({ id: aNewPlayId, gameId: azul.id }) as unknown,
    }),
  ])
})

test("a play is kept, too, when the server doesn't answer although the device is online", async () => {
  server.use(
    http.post("*/api/plays/preview", () => HttpResponse.error()),
    http.post("*/api/plays", () => HttpResponse.error()),
  )
  renderApp(`/plays/new?game=${azul.id}`)

  await userEvent.selectOptions(await page.findByTestId("players-to-add"), "Dora")
  await userEvent.type(scoreField("Bence"), "48")
  await userEvent.type(scoreField("Dora"), "61")
  await userEvent.click(page.getByTestId("save-play"))

  expect((await waitingList()).getByTestId("waiting-play-status")).toHaveTextContent(
    "Waiting for a connection",
  )
})

test("a waiting play is sent, under its id, once Board Keeper can be reached", async () => {
  keepWaiting(waitingPlay())
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()

  renderApp("/")

  await waitFor(() =>
    expect(sent).toEqual([expect.objectContaining({ id: waitingPlay().play.id, gameId: azul.id })]),
  )
  await waitFor(() => expect(page.queryByTestId("waiting-plays")).not.toBeInTheDocument())
  expect(localStorage.getItem("boardkeeper.waitingPlays")).toBe("[]")
})

test("a waiting play isn't sent while the device is offline", async () => {
  const sent = playsCanBeRecorded()
  renderApp("/")
  await page.findByTestIdWith("game-row", "Azul")
  onlineManager.setOnline(false)

  keepWaiting(waitingPlay())

  expect((await waitingList()).getByTestId("waiting-play-status")).toHaveTextContent(
    "Waiting for a connection",
  )
  expect(sent).toEqual([])

  playsCanBePreviewed()
  onlineManager.setOnline(true)
  await waitFor(() => expect(sent).toHaveLength(1))
})

test("a tie only the recorder can decide waits for them, and is decided in the form", async () => {
  // Bence and Dora both scored 61, and nobody was asked who won.
  const tied = waitingPlay()
  tied.play.players[0].scores = { total: 61 }
  keepWaiting(tied)
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()
  renderApp("/")

  const list = await waitingList()
  expect(await list.findByTestId("waiting-play-status")).toHaveTextContent(
    "Players are tied for first place, and the scores can't decide who won. Open it to choose.",
  )
  expect(sent).toEqual([])

  await userEvent.click(list.getByTestId("open-waiting-play"))
  await userEvent.click(await page.findByTestId("tie-winner-Dora"))
  await userEvent.click(page.getByTestId("save-play"))

  await waitFor(() => expect(sent).toHaveLength(1))
  expect(sent[0]).toMatchObject({
    id: tied.play.id,
    players: [
      { playerId: bencePlayer.id, winner: false },
      { playerId: doraPlayer.id, winner: true },
    ],
  })
  expect(localStorage.getItem("boardkeeper.waitingPlays")).toBe("[]")
})

test("a play the server refuses waits for the recorder, with the reason", async () => {
  keepWaiting(waitingPlay())
  playsCanBePreviewed()
  server.use(
    http.post("*/api/plays", () =>
      HttpResponse.json(
        { status: 422, code: "plays.invalidEntry", reason: "plays.invalid.tooFewPlayers" },
        { status: 422 },
      ),
    ),
  )
  renderApp("/")

  const list = await waitingList()
  expect(await list.findByTestId("open-waiting-play")).toBeInTheDocument()
  expect(list.getByTestId("waiting-play-status")).not.toHaveTextContent("Waiting for a connection")
})

test("a play that waits because the server couldn't be reached is tried again later, not marked", async () => {
  keepWaiting(waitingPlay())
  playsCanBePreviewed()
  server.use(http.post("*/api/plays", () => problem(503, "unavailable")))
  renderApp("/")

  const list = await waitingList()
  await waitFor(() =>
    expect(list.getByTestId("waiting-play-status")).toHaveTextContent("Waiting for a connection"),
  )
  expect(list.queryByTestId("open-waiting-play")).not.toBeInTheDocument()
})

test("another account's waiting plays are neither shown nor sent", async () => {
  keepWaiting(waitingPlay({ accountId: anna.id }))
  const sent = playsCanBeRecorded()
  renderApp("/")

  await page.findByTestIdWith("game-row", "Azul")
  expect(page.queryByTestId("waiting-plays")).not.toBeInTheDocument()
  expect(sent).toEqual([])
})

test("a waiting play can be thrown away after asking", async () => {
  renderApp("/")
  await page.findByTestIdWith("game-row", "Azul")
  onlineManager.setOnline(false)
  keepWaiting(waitingPlay())

  const remove = (await waitingList()).getByTestId("remove-waiting-play")
  expect(remove).toHaveAccessibleName(/^Remove the play of Azul on/)
  await userEvent.click(remove)
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Remove this play?")
  await userEvent.click(page.getByTestId("confirm"))

  await waitFor(() => expect(page.queryByTestId("waiting-plays")).not.toBeInTheDocument())
  expect(localStorage.getItem("boardkeeper.waitingPlays")).toBe("[]")
})
