import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play } from "@/api/client"
import {
  anna,
  annaPlayer,
  bence,
  bencePlayer,
  azul,
  azulPlay,
  doraPlayer,
  historyIs,
  pandemicPlay,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  problem,
  signedInAs,
} from "@/test/api"
import { scoreField } from "@/test/playForm"
import { inside, page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

/** The lines of the history: a link to each play. */
function summaries() {
  return page.queryAllByTestId("play-summary")
}

test("the navigation leads to the plays, newest first, with who won", async () => {
  signedInAs(bence)
  historyIs([pandemicPlay, azulPlay])
  renderApp("/")

  await userEvent.click(await page.findByTestId("nav-plays"))

  expect(await page.findByTestIdWith("page-title", "Plays")).toBeInTheDocument()
  const [pandemic, azulLine] = await waitFor(() => {
    const lines = summaries()
    expect(lines).toHaveLength(2)
    return lines
  })
  expect(pandemic).toHaveTextContent("Pandemic15 March 2026Lost together (17): Anna, Bence")
  // The winner comes first and is marked, whatever the order they were entered in.
  expect(azulLine).toHaveTextContent("Azul14 March 2026Anna74Dora61")
  expect(inside(azulLine).getByTestId("winner")).toHaveAccessibleName("Winner")
  expect(azulLine).toHaveAttribute("href", `/plays/${azulPlay.id}`)
})

test("says so when nothing has been recorded yet, or the plays can't be loaded", async () => {
  signedInAs(bence)
  historyIs([])
  const { unmount } = renderApp("/plays")
  expect(await page.findByTestId("no-plays")).toHaveTextContent(/No plays yet\./)
  unmount()

  server.use(http.get("*/api/plays", () => problem(500, "unexpected")))
  const { queryClient } = await import("@/api/queryClient")
  queryClient.clear()
  renderApp("/plays")
  expect(await page.findByTestId("load-failed")).toHaveTextContent("Couldn't load the plays")
})

test("narrows the plays by game, player and dates, and remembers that in the address", async () => {
  signedInAs(bence)
  const asked = historyIs([azulPlay])
  renderApp("/plays")

  await waitFor(() => expect(summaries()).toHaveLength(1))
  expect(asked[0].get("gameId")).toBeNull()
  await userEvent.selectOptions(page.getByTestId("filter-game"), "Azul")
  await userEvent.selectOptions(page.getByTestId("filter-player"), "Dora")
  await userEvent.type(page.getByTestId("filter-from"), "2026-03-01")
  await userEvent.type(page.getByTestId("filter-to"), "2026-03-31")

  await waitFor(() => {
    const last = asked.at(-1)
    expect(last?.get("gameId")).toBe(azul.id)
    expect(last?.getAll("playerId")).toEqual([doraPlayer.id])
    expect(last?.get("from")).toBe("2026-03-01")
    expect(last?.get("to")).toBe("2026-03-31")
  })

  await userEvent.click(page.getByTestId("clear-filters"))
  await waitFor(() => expect(asked.at(-1)?.get("gameId")).toBeNull())
  expect(page.getByTestId("filter-game")).toHaveValue("")
  expect(page.queryByTestId("clear-filters")).not.toBeInTheDocument()
})

test('"My plays" asks for the plays the signed-in player took part in', async () => {
  signedInAs(bence)
  const asked = historyIs([azulPlay])
  renderApp("/plays")

  await userEvent.click(await page.findByTestId("filter-mine"))
  await waitFor(() => expect(asked.at(-1)?.getAll("playerId")).toEqual([bencePlayer.id]))

  // Together with another player: the plays both took part in.
  await userEvent.selectOptions(page.getByTestId("filter-player"), "Anna")
  await waitFor(() =>
    expect(asked.at(-1)?.getAll("playerId")).toEqual([bencePlayer.id, annaPlayer.id]),
  )
})

test("starts from the filters in the address, and says when nothing matches them", async () => {
  signedInAs(bence)
  const asked = historyIs([])
  renderApp(`/plays?game=${azul.id}&mine=1`)

  expect(await page.findByTestId("no-plays")).toHaveTextContent("No plays match these filters.")
  expect(asked[0].get("gameId")).toBe(azul.id)
  expect(asked[0].getAll("playerId")).toEqual([bencePlayer.id])
  expect(page.getByTestId("filter-mine")).toBeChecked()
  await waitFor(() => expect(page.getByTestId("filter-game")).toHaveValue(azul.id))
})

test('"Show more" adds the next page of plays below the first', async () => {
  signedInAs(bence)
  const asked = historyIs([azulPlay], [pandemicPlay])
  renderApp("/plays")

  await userEvent.click(await page.findByTestId("show-more"))

  await waitFor(() => expect(summaries()).toHaveLength(2))
  expect(asked.map((query) => query.get("after"))).toEqual(["", "page-2"])
  // That was the last page.
  expect(page.queryByTestId("show-more")).not.toBeInTheDocument()
})

test("a play can be edited by someone who may change it", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  playCanBeEntered()
  playsCanBePreviewed()
  const sent: NewPlay[] = []
  const changed: Play = {
    ...azulPlay,
    location: "At Anna's",
    results: [
      { ...azulPlay.results[0], scores: { total: 80 }, total: 80, placing: 1, won: true },
      { ...azulPlay.results[1], placing: 2, won: false },
    ],
  }
  server.use(
    http.put(`*/api/plays/${azulPlay.id}`, async ({ request }) => {
      sent.push((await request.json()) as NewPlay)
      playIs(changed)
      return HttpResponse.json(changed)
    }),
  )
  renderApp(`/plays/${azulPlay.id}`)

  await userEvent.click(await page.findByTestId("play-actions"))
  await userEvent.click(await page.findByTestId("edit-play"))

  // The form starts with what was recorded.
  expect(await page.findByTestIdWith("page-title", "Edit a play")).toBeInTheDocument()
  expect(page.getByTestId("played-on")).toHaveValue("2026-03-14")
  expect(page.getByTestId("location")).toHaveValue("At Dora's")
  expect(page.getByTestId("notes")).toHaveValue("Dora's first play.")
  expect(scoreField("Dora")).toHaveValue(61)
  expect(scoreField("Anna")).toHaveValue(74)

  await userEvent.clear(scoreField("Dora"))
  await userEvent.type(scoreField("Dora"), "80")
  await userEvent.clear(page.getByTestId("location"))
  await userEvent.type(page.getByTestId("location"), "At Anna's")
  await userEvent.click(page.getByTestId("save-play"))

  // Back on the play's page, with what the server answered.
  expect(
    await page.findByTestIdWith("page-description", "14 March 2026 · At Anna's"),
  ).toBeInTheDocument()
  expect(sent).toEqual([
    {
      gameId: azul.id,
      playedOn: "2026-03-14",
      location: "At Anna's",
      notes: "Dora's first play.",
      recordingMode: "POINTS",
      setup: { cooperative: "false", lowestScoreWins: "false" },
      players: [
        { playerId: doraPlayer.id, scores: { total: 80 }, winner: false, setup: {} },
        { playerId: annaPlayer.id, scores: { total: 74 }, winner: false, setup: {} },
      ],
    },
  ])
})

test("a play can be deleted after an 'are you sure?'", async () => {
  signedInAs(bence)
  playIs(azulPlay)
  historyIs([])
  let deleted = false
  server.use(
    http.delete(`*/api/plays/${azulPlay.id}`, () => {
      deleted = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp(`/plays/${azulPlay.id}`)

  await userEvent.click(await page.findByTestId("play-actions"))
  await userEvent.click(await page.findByTestId("delete-play"))
  expect(await page.findByTestId("confirm-dialog")).toHaveAccessibleName("Delete this play?")
  expect(deleted).toBe(false)
  await userEvent.click(page.getByTestId("confirm"))

  // Back at the history, which no longer has it.
  expect(await page.findByTestIdWith("page-title", "Plays")).toBeInTheDocument()
  expect(deleted).toBe(true)
})

test("someone who may not change a play is offered neither, and can't open its edit page", async () => {
  signedInAs(anna)
  const play = { ...azulPlay, canChange: false }
  playIs(play)
  playCanBeEntered()
  const { unmount } = renderApp(`/plays/${play.id}`)

  await page.findByTestIdWith("page-title", "Azul")
  expect(page.queryByTestId("play-actions")).not.toBeInTheDocument()
  unmount()

  renderApp(`/plays/${play.id}/edit`)
  expect(
    await page.findByTestIdWith("page-title", "You can't change this play"),
  ).toBeInTheDocument()
  expect(page.getByTestId("back-to-play")).toHaveAttribute("href", `/plays/${play.id}`)
})
