import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, expect, test } from "vitest"
import type { NewPlay } from "@/api/client"
import {
  annaPlayer,
  azul,
  azulPlay,
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  playCanBeEntered,
  playIs,
  playsCanBePreviewed,
  signedInAs,
} from "@/test/api"
import { findScoreField, playerRow, scoreField } from "@/test/playForm"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"
import { announcements, sortableListsCanBeUsed } from "@/test/sortable"

beforeEach(() => {
  sortableListsCanBeUsed()
  signedInAs(bence)
  gamesAre([azul])
  playCanBeEntered()
  playsCanBePreviewed()
})

/** Answers "record a play", and remembers what was sent. */
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

async function addPlayer(name: string) {
  await userEvent.selectOptions(await page.findByTestId("players-to-add"), name)
}

/** The names of the Players in the form, in their order, read from their handles. */
function playersInOrder() {
  return page
    .getAllByTestId("move-handle")
    .map((handle) => handle.getAttribute("aria-label")?.replace("Move ", ""))
}

test("players can be put in another order from the keyboard, and are recorded in it", async () => {
  const sent = playsCanBeRecorded()
  renderApp(`/plays/new?game=${azul.id}`)
  await addPlayer("Anna")
  await addPlayer("Dora")
  await userEvent.type(scoreField("Bence"), "48")
  await userEvent.type(scoreField("Anna"), "52")
  await userEvent.type(scoreField("Dora"), "61")
  const said = announcements()

  // Space picks Dora up, the arrow moves her up past Anna, and Space puts her down.
  playerRow("Dora").getByTestId("move-handle").focus()
  await userEvent.keyboard(" ")
  await userEvent.keyboard("{ArrowUp}")
  // A screen reader is told where she is once she stays there a moment.
  await waitFor(() => expect(said).toContain("Dora is now number 2 of 3."))
  await userEvent.keyboard(" ")

  await waitFor(() => expect(playersInOrder()).toEqual(["Bence", "Dora", "Anna"]))
  expect(said).toEqual([
    "Picked up Dora, number 3 of 3.",
    "Dora is now number 2 of 3.",
    "Put down Dora as number 2 of 3.",
  ])
  // Her handle keeps the focus, so she can be moved on, and her score went with her.
  expect(playerRow("Dora").getByTestId("move-handle")).toHaveFocus()
  expect(scoreField("Dora")).toHaveValue(61)

  await userEvent.click(page.getByTestId("save-play"))
  await page.findByTestIdWith("page-title", "Azul")
  expect(sent[0].players.map(({ playerId, scores }) => ({ playerId, scores }))).toEqual([
    { playerId: bencePlayer.id, scores: { total: 48 } },
    { playerId: doraPlayer.id, scores: { total: 61 } },
    { playerId: annaPlayer.id, scores: { total: 52 } },
  ])
})

test("Escape puts a player back where they were", async () => {
  renderApp(`/plays/new?game=${azul.id}`)
  await addPlayer("Anna")
  const said = announcements()

  playerRow("Anna").getByTestId("move-handle").focus()
  await userEvent.keyboard(" ")
  await userEvent.keyboard("{ArrowUp}")
  await waitFor(() => expect(said).toContain("Anna is now number 1 of 2."))
  await userEvent.keyboard("{Escape}")

  await waitFor(() => expect(said.at(-1)).toBe("Moving cancelled. Anna is back as number 2 of 2."))
  expect(playersInOrder()).toEqual(["Bence", "Anna"])
})

test("a player on their own has nothing to be moved past", async () => {
  renderApp(`/plays/new?game=${azul.id}`)

  // Whoever records is put in as the first player.
  expect(await findScoreField("Bence")).toBeInTheDocument()
  // The handle is hidden, so screen readers aren't offered it either.
  expect(playerRow("Bence").getByTestId("move-handle")).not.toBeVisible()

  await addPlayer("Anna")
  expect(playersInOrder()).toEqual(["Bence", "Anna"])
  // Only the handle moves a Player; the row stays a plain row.
  expect(scoreField("Bence").closest("li")).not.toHaveAttribute("role")
})
