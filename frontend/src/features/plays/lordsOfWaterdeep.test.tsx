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
import { optionOf, playerRow } from "@/test/playForm"
import { page } from "@/test/queries"
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
  await page.findByTestId("players-to-add")
}

test("a sixth player can be added once a half of Scoundrels of Skullport is ticked", async () => {
  await startRecording()
  for (const name of ["Anna", "Dora", "Eszter", "Feri"]) {
    await userEvent.selectOptions(page.getByTestId("players-to-add"), name)
  }

  // Five is the most without an Expansion that allows more.
  expect(page.queryByTestId("players-to-add")).not.toBeInTheDocument()
  expect(page.getByTestId("players-full")).toHaveTextContent(
    "This game is for at most 5 players, unless it's played with an Expansion that allows more.",
  )
  await userEvent.click(page.getByTestId("expansion-undermountain"))
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Gabi")
  expect(page.getByTestId("players-full")).toHaveTextContent("This game is for at most 6 players.")

  // Taking the Expansion off again says at once that there are too many.
  await userEvent.click(page.getByTestId("expansion-undermountain"))
  expect(
    page.getByTestIdWith(
      "field-error",
      "This game is for at most 5 players, unless it's played with an Expansion that allows more.",
    ),
  ).toBeInTheDocument()
  // Nobody is taken off: the recorder decides who.
  expect(playerRow("Gabi").getByTestId("remove-player")).toBeInTheDocument()
})

test("each half brings its own lords, and skullport a row for corruption", async () => {
  await startRecording()
  await userEvent.selectOptions(page.getByTestId("players-to-add"), "Anna")

  const lord = playerRow("Bence").getByTestId("player-setup-lord")
  expect(optionOf(lord, "trobriand")).toBeUndefined()
  expect(optionOf(lord, "theXanathar")).toBeUndefined()
  expect(
    page.queryByTestIdWith("score-pad-category", "Corruption (tokens × the farthest empty space)"),
  ).not.toBeInTheDocument()

  await userEvent.click(page.getByTestId("expansion-skullport"))
  expect(optionOf(lord, "theXanathar")).toHaveTextContent("The Xanathar")
  expect(optionOf(lord, "trobriand")).toBeUndefined()
  expect(
    page.getByTestIdWith("score-pad-category", "Corruption (tokens × the farthest empty space)"),
  ).toBeInTheDocument()
})
