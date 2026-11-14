import { onlineManager } from "@tanstack/react-query"
import { screen, waitFor, within } from "@testing-library/react"
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
  const heading = await screen.findByRole("heading", { name: "Not sent yet" })
  return heading.closest("section") as HTMLElement
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
  await screen.findByLabelText("Score of Bence")
  onlineManager.setOnline(false)

  expect(
    await screen.findByText(
      "You're offline. This play will be kept on this device and sent once there's a connection. Its totals and placings are worked out then.",
    ),
  ).toBeInTheDocument()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Dora")
  await userEvent.type(screen.getByLabelText("Score of Bence"), "48")
  await userEvent.type(screen.getByLabelText("Score of Dora"), "61")
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  const list = await waitingList()
  expect(within(list).getByText(/^Azul ·/)).toBeInTheDocument()
  expect(within(list).getByText("Bence, Dora")).toBeInTheDocument()
  expect(within(list).getByText("Waiting for a connection")).toBeInTheDocument()
  expect(screen.getAllByText(/You're offline\. Plays you record are kept/)).not.toHaveLength(0)
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

  await userEvent.selectOptions(await screen.findByLabelText("Add a player"), "Dora")
  await userEvent.type(screen.getByLabelText("Score of Bence"), "48")
  await userEvent.type(screen.getByLabelText("Score of Dora"), "61")
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

  expect(within(await waitingList()).getByText("Waiting for a connection")).toBeInTheDocument()
})

test("a waiting play is sent, under its id, once Board Keeper can be reached", async () => {
  keepWaiting(waitingPlay())
  playsCanBePreviewed()
  const sent = playsCanBeRecorded()

  renderApp("/")

  await waitFor(() =>
    expect(sent).toEqual([expect.objectContaining({ id: waitingPlay().play.id, gameId: azul.id })]),
  )
  await waitFor(() =>
    expect(screen.queryByRole("heading", { name: "Not sent yet" })).not.toBeInTheDocument(),
  )
  expect(localStorage.getItem("boardkeeper.waitingPlays")).toBe("[]")
})

test("a waiting play isn't sent while the device is offline", async () => {
  const sent = playsCanBeRecorded()
  renderApp("/")
  await screen.findByRole("row", { name: /Azul/ })
  onlineManager.setOnline(false)

  keepWaiting(waitingPlay())

  expect(within(await waitingList()).getByText("Waiting for a connection")).toBeInTheDocument()
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
  expect(
    await within(list).findByText(
      "Players are tied for first place, and the scores can't decide who won. Open it to choose.",
    ),
  ).toBeInTheDocument()
  expect(sent).toEqual([])

  await userEvent.click(within(list).getByRole("link", { name: "Open" }))
  await userEvent.click(await screen.findByRole("radio", { name: "Dora" }))
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))

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
  expect(await within(list).findByRole("link", { name: "Open" })).toBeInTheDocument()
  expect(within(list).queryByText("Waiting for a connection")).not.toBeInTheDocument()
})

test("a play that waits because the server couldn't be reached is tried again later, not marked", async () => {
  keepWaiting(waitingPlay())
  playsCanBePreviewed()
  server.use(http.post("*/api/plays", () => problem(503, "unavailable")))
  renderApp("/")

  const list = await waitingList()
  await waitFor(() =>
    expect(within(list).getByText("Waiting for a connection")).toBeInTheDocument(),
  )
  expect(within(list).queryByRole("link", { name: "Open" })).not.toBeInTheDocument()
})

test("another account's waiting plays are neither shown nor sent", async () => {
  keepWaiting(waitingPlay({ accountId: anna.id }))
  const sent = playsCanBeRecorded()
  renderApp("/")

  await screen.findByRole("row", { name: /Azul/ })
  expect(screen.queryByRole("heading", { name: "Not sent yet" })).not.toBeInTheDocument()
  expect(sent).toEqual([])
})

test("a waiting play can be thrown away after asking", async () => {
  renderApp("/")
  await screen.findByRole("row", { name: /Azul/ })
  onlineManager.setOnline(false)
  keepWaiting(waitingPlay())

  await userEvent.click(
    within(await waitingList()).getByRole("button", { name: /^Remove the play of Azul on/ }),
  )
  const dialog = await screen.findByRole("alertdialog", { name: "Remove this play?" })
  await userEvent.click(within(dialog).getByRole("button", { name: "Remove play" }))

  await waitFor(() =>
    expect(screen.queryByRole("heading", { name: "Not sent yet" })).not.toBeInTheDocument(),
  )
  expect(localStorage.getItem("boardkeeper.waitingPlays")).toBe("[]")
})
