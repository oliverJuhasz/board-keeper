import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import {
  anna,
  annaPlayer,
  bence,
  bencePlayer,
  calico,
  calicoPlay,
  doraPlayer,
  historyIs,
  pandemicPlay,
  playCanBeEntered,
  playIs,
  problem,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** The lines of the history: one list item with a link for each play. */
function summaries() {
  return screen.getAllByRole("listitem").filter((item) => within(item).queryByRole("link"))
}

test("the navigation leads to the plays, newest first, with who won", async () => {
  signedInAs(bence)
  historyIs([pandemicPlay, calicoPlay])
  renderApp("/")

  await userEvent.click(await screen.findByRole("link", { name: "Plays" }))

  expect(await screen.findByRole("heading", { name: "Plays" })).toBeInTheDocument()
  const [pandemic, calicoLine] = await waitFor(() => {
    const lines = summaries()
    expect(lines).toHaveLength(2)
    return lines
  })
  expect(pandemic).toHaveTextContent("Pandemic15 March 2026Lost together (17): Anna, Bence")
  // The winner comes first and is marked, whatever the order they were entered in.
  expect(calicoLine).toHaveTextContent("Calico14 March 2026Anna74Dora61")
  expect(within(calicoLine).getByRole("img", { name: "Winner" })).toBeInTheDocument()
  expect(within(calicoLine).getByRole("link")).toHaveAttribute("href", `/plays/${calicoPlay.id}`)
})

test("says so when nothing has been recorded yet, or the plays can't be loaded", async () => {
  signedInAs(bence)
  historyIs([])
  const { unmount } = renderApp("/plays")
  expect(await screen.findByText(/No plays yet\./)).toBeInTheDocument()
  unmount()

  server.use(http.get("*/api/plays", () => problem(500, "unexpected")))
  const { queryClient } = await import("@/api/queryClient")
  queryClient.clear()
  renderApp("/plays")
  expect(await screen.findByText("Couldn't load the plays")).toBeInTheDocument()
})

test("narrows the plays by game, player and dates, and remembers that in the address", async () => {
  signedInAs(bence)
  const asked = historyIs([calicoPlay])
  renderApp("/plays")

  await waitFor(() => expect(summaries()).toHaveLength(1))
  expect(asked[0].get("gameId")).toBeNull()
  await userEvent.selectOptions(screen.getByLabelText("Game"), "Calico")
  await userEvent.selectOptions(screen.getByLabelText("Player"), "Dora")
  await userEvent.type(screen.getByLabelText("From"), "2026-03-01")
  await userEvent.type(screen.getByLabelText("Until"), "2026-03-31")

  await waitFor(() => {
    const last = asked.at(-1)
    expect(last?.get("gameId")).toBe(calico.id)
    expect(last?.getAll("playerId")).toEqual([doraPlayer.id])
    expect(last?.get("from")).toBe("2026-03-01")
    expect(last?.get("to")).toBe("2026-03-31")
  })

  await userEvent.click(screen.getByRole("button", { name: "Clear filters" }))
  await waitFor(() => expect(asked.at(-1)?.get("gameId")).toBeNull())
  expect(screen.getByLabelText("Game")).toHaveValue("")
  expect(screen.queryByRole("button", { name: "Clear filters" })).not.toBeInTheDocument()
})

test('"My plays" asks for the plays the signed-in player took part in', async () => {
  signedInAs(bence)
  const asked = historyIs([calicoPlay])
  renderApp("/plays")

  await userEvent.click(await screen.findByRole("checkbox", { name: "My plays" }))
  await waitFor(() => expect(asked.at(-1)?.getAll("playerId")).toEqual([bencePlayer.id]))

  // Together with another player: the plays both took part in.
  await userEvent.selectOptions(screen.getByLabelText("Player"), "Anna")
  await waitFor(() =>
    expect(asked.at(-1)?.getAll("playerId")).toEqual([bencePlayer.id, annaPlayer.id]),
  )
})

test("starts from the filters in the address, and says when nothing matches them", async () => {
  signedInAs(bence)
  const asked = historyIs([])
  renderApp(`/plays?game=${calico.id}&mine=1`)

  expect(await screen.findByText("No plays match these filters.")).toBeInTheDocument()
  expect(asked[0].get("gameId")).toBe(calico.id)
  expect(asked[0].getAll("playerId")).toEqual([bencePlayer.id])
  expect(screen.getByRole("checkbox", { name: "My plays" })).toBeChecked()
  await waitFor(() => expect(screen.getByLabelText("Game")).toHaveValue(calico.id))
})

test('"Show more" adds the next page of plays below the first', async () => {
  signedInAs(bence)
  const asked = historyIs([calicoPlay], [pandemicPlay])
  renderApp("/plays")

  await userEvent.click(await screen.findByRole("button", { name: "Show more" }))

  await waitFor(() => expect(summaries()).toHaveLength(2))
  expect(asked.map((query) => query.get("after"))).toEqual(["", "page-2"])
  // That was the last page.
  expect(screen.queryByRole("button", { name: "Show more" })).not.toBeInTheDocument()
})

test("a play can be edited by someone who may change it", async () => {
  signedInAs(bence)
  playIs(calicoPlay)
  playCanBeEntered()
  const sent: NewPlay[] = []
  const changed: Play = {
    ...calicoPlay,
    location: "At Anna's",
    results: [
      { ...calicoPlay.results[0], scores: { total: 80 }, total: 80, placing: 1, won: true },
      { ...calicoPlay.results[1], placing: 2, won: false },
    ],
  }
  server.use(
    http.put(`*/api/plays/${calicoPlay.id}`, async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      playIs(changed)
      return HttpResponse.json(changed)
    }),
  )
  renderApp(`/plays/${calicoPlay.id}`)

  await userEvent.click(await screen.findByRole("button", { name: "Actions for this play" }))
  await userEvent.click(await screen.findByRole("menuitem", { name: "Edit" }))

  // The form starts with what was recorded.
  expect(await screen.findByRole("heading", { name: "Edit a play" })).toBeInTheDocument()
  expect(screen.getByLabelText("Date")).toHaveValue("2026-03-14")
  expect(screen.getByLabelText("Location")).toHaveValue("At Dora's")
  expect(screen.getByLabelText("Notes")).toHaveValue("Dora's first play.")
  expect(screen.getByLabelText("Score of Dora")).toHaveValue(61)
  expect(screen.getByLabelText("Score of Anna")).toHaveValue(74)

  await userEvent.clear(screen.getByLabelText("Score of Dora"))
  await userEvent.type(screen.getByLabelText("Score of Dora"), "80")
  await userEvent.clear(screen.getByLabelText("Location"))
  await userEvent.type(screen.getByLabelText("Location"), "At Anna's")
  await userEvent.click(screen.getByRole("button", { name: "Save play" }))

  // Back on the play's page, with what the server answered.
  expect(await screen.findByText("14 March 2026 · At Anna's")).toBeInTheDocument()
  expect(sent).toEqual([
    {
      gameId: calico.id,
      playedOn: "2026-03-14",
      location: "At Anna's",
      notes: "Dora's first play.",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: doraPlayer.id, scores: { total: 80 }, winner: false },
        { playerId: annaPlayer.id, scores: { total: 74 }, winner: false },
      ],
    },
  ])
})

test("a play can be deleted after an 'are you sure?'", async () => {
  signedInAs(bence)
  playIs(calicoPlay)
  historyIs([])
  let deleted = false
  server.use(
    http.delete(`*/api/plays/${calicoPlay.id}`, () => {
      deleted = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp(`/plays/${calicoPlay.id}`)

  await userEvent.click(await screen.findByRole("button", { name: "Actions for this play" }))
  await userEvent.click(await screen.findByRole("menuitem", { name: "Delete" }))
  const dialog = await screen.findByRole("alertdialog", { name: "Delete this play?" })
  expect(deleted).toBe(false)
  await userEvent.click(within(dialog).getByRole("button", { name: "Delete play" }))

  // Back at the history, which no longer has it.
  expect(await screen.findByRole("heading", { name: "Plays" })).toBeInTheDocument()
  expect(deleted).toBe(true)
})

test("someone who may not change a play is offered neither, and can't open its edit page", async () => {
  signedInAs(anna)
  const play = { ...calicoPlay, canChange: false }
  playIs(play)
  playCanBeEntered()
  const { unmount } = renderApp(`/plays/${play.id}`)

  await screen.findByRole("heading", { name: "Calico" })
  expect(screen.queryByRole("button", { name: "Actions for this play" })).not.toBeInTheDocument()
  unmount()

  renderApp(`/plays/${play.id}/edit`)
  expect(
    await screen.findByRole("heading", { name: "You can't change this play" }),
  ).toBeInTheDocument()
  expect(screen.getByRole("link", { name: "Back to the play" })).toHaveAttribute(
    "href",
    `/plays/${play.id}`,
  )
})
