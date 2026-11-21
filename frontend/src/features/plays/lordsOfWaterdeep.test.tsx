import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { Player } from "@/api/client"
import {
  annaPlayer,
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  lordsOfWaterdeep,
  playCanBeEntered,
  playsCanBePreviewed,
  signedInAs,
} from "@/test/api"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// An Expansion that changes the basic rules: Lords of Waterdeep is for five, and six with either
// half of Scoundrels of Skullport. Each half brings Lords of its own, and Skullport adds a row.

const sixPlayers: Player[] = [
  annaPlayer,
  bencePlayer,
  doraPlayer,
  { id: "0193a000-0000-7000-8000-0000000000b4", name: "Eszter" },
  { id: "0193a000-0000-7000-8000-0000000000b5", name: "Feri" },
  { id: "0193a000-0000-7000-8000-0000000000b6", name: "Gabi" },
]

async function startRecording() {
  signedInAs(bence)
  gamesAre([lordsOfWaterdeep])
  playCanBeEntered()
  playsCanBePreviewed()
  server.use(http.get("*/api/players", () => HttpResponse.json(sixPlayers)))
  renderApp(`/plays/new?game=${lordsOfWaterdeep.id}`)
  await screen.findByLabelText("Add a player")
}

test("a sixth player can be added once a half of Scoundrels of Skullport is ticked", async () => {
  await startRecording()
  for (const name of ["Anna", "Dora", "Eszter", "Feri"]) {
    await userEvent.selectOptions(screen.getByLabelText("Add a player"), name)
  }

  // Five is the most without an Expansion that allows more.
  expect(screen.queryByLabelText("Add a player")).not.toBeInTheDocument()
  expect(
    screen.getByText(
      "This game is for at most 5 players, unless it's played with an Expansion that allows more.",
    ),
  ).toBeInTheDocument()
  await userEvent.click(
    screen.getByRole("checkbox", { name: "Scoundrels of Skullport: Undermountain" }),
  )
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Gabi")
  expect(screen.getByText("This game is for at most 6 players.")).toBeInTheDocument()

  // Taking the Expansion off again says at once that there are too many.
  await userEvent.click(
    screen.getByRole("checkbox", { name: "Scoundrels of Skullport: Undermountain" }),
  )
  expect(
    screen.getByText(
      "This game is for at most 5 players, unless it's played with an Expansion that allows more.",
    ),
  ).toBeInTheDocument()
  // Nobody is taken off: the recorder decides who.
  expect(screen.getByRole("button", { name: "Remove Gabi" })).toBeInTheDocument()
})

test("each half brings its own lords, and skullport a row for corruption", async () => {
  await startRecording()
  await userEvent.selectOptions(screen.getByLabelText("Add a player"), "Anna")

  const lord = screen.getByLabelText("Lord of Waterdeep of Bence")
  expect(within(lord).queryByRole("option", { name: "Trobriand" })).not.toBeInTheDocument()
  expect(within(lord).queryByRole("option", { name: "The Xanathar" })).not.toBeInTheDocument()
  expect(
    screen.queryByText("Corruption (tokens × the farthest empty space)"),
  ).not.toBeInTheDocument()

  await userEvent.click(
    screen.getByRole("checkbox", { name: "Scoundrels of Skullport: Skullport" }),
  )
  expect(within(lord).getByRole("option", { name: "The Xanathar" })).toBeInTheDocument()
  expect(within(lord).queryByRole("option", { name: "Trobriand" })).not.toBeInTheDocument()
  expect(screen.getByText("Corruption (tokens × the farthest empty space)")).toBeInTheDocument()
})
