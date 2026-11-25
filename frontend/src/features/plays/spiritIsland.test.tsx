import { waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play, PlayPreview, PlayPreviewRequest } from "@/api/client"
import {
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  playCanBeEntered,
  playIs,
  signedInAs,
  spiritIsland,
} from "@/test/api"
import { spiritIslandModule } from "@/test/gameModules"
import { playerRow } from "@/test/playForm"
import { page } from "@/test/queries"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// A cooperative Game with a module of its own: Spirit Island is won or lost together, and
// scoring it is optional. With a score, the group's rows are filled in and the module works out
// the score. An Adversary's level is a number, chosen once there is an Adversary.

/** A Play of Spirit Island as the server would answer it: won against England at level 3. */
function spiritPlay(overrides: Partial<Play> = {}): Play {
  return {
    id: "0193a000-0000-7000-8000-0000000000fa",
    game: { id: spiritIsland.id, name: spiritIsland.name, moduleKey: "spirit-island" },
    gameModuleId: spiritIslandModule.id,
    playedOn: "2026-03-14",
    recordingMode: "POINTS",
    setup: { adversary: "england", englandLevel: "3", thematicMap: "false" },
    cooperative: true,
    group: { won: true, scores: { invaderCards: 4, dahan: 7, blight: 5 }, total: 49 },
    results: [
      {
        player: bencePlayer,
        scores: {},
        setup: { spirit: "thunderspeaker" },
        markedWinner: false,
        won: true,
      },
      {
        player: doraPlayer,
        scores: {},
        setup: { spirit: "oceansHungryGrasp" },
        markedWinner: false,
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

/** Works out the group's score as the module would for England at level 3 and two players. */
function scoreCanBePreviewed() {
  server.use(
    http.post("*/api/plays/preview", async ({ request }) => {
      const body = (await request.json()) as PlayPreviewRequest
      const rows = body.group?.scores ?? {}
      const won = body.group?.won ?? false
      const groups = Math.floor((rows.dahan ?? 0) / 2) - Math.floor((rows.blight ?? 0) / 2)
      const total = won
        ? 5 * 6 + 10 + 2 * (rows.invaderCards ?? 0) + groups
        : 2 * 6 + (rows.invaderCards ?? 0) + groups
      const preview: PlayPreview = {
        cooperative: true,
        group: { won, scores: rows, total },
        results: body.players.map((player) => ({ playerId: player.playerId, won })),
        tieForRecorder: [],
      }
      return HttpResponse.json(preview)
    }),
  )
}

async function startRecording() {
  signedInAs(bence)
  gamesAre([spiritIsland])
  playCanBeEntered()
  renderApp(`/plays/new?game=${spiritIsland.id}`)
  await userEvent.selectOptions(await page.findByTestId("players-to-add"), "Dora")
  await userEvent.selectOptions(
    playerRow("Bence").getByTestId("player-setup-spirit"),
    "Thunderspeaker",
  )
  await userEvent.selectOptions(
    playerRow("Dora").getByTestId("player-setup-spirit"),
    "Ocean's Hungry Grasp",
  )
}

test("a play starts with only whether the spirits won, against an adversary at its level", async () => {
  const sent = playsCanBeRecorded(spiritPlay({ recordingMode: "WINNER_ONLY" }))
  await startRecording()

  expect(page.getByTestId("recorded-as")).toHaveTextContent("Won or lost only")
  // The level is chosen once there is an adversary, and only that adversary's.
  expect(page.queryByTestId("variant-englandLevel")).not.toBeInTheDocument()
  await userEvent.click(page.getByTestId("variant-adversary-england"))
  const level = page.getByTestId("variant-englandLevel")
  expect(level).toHaveValue("0")
  expect(level).toHaveTextContent("Base level")
  expect(page.queryByTestId("variant-swedenLevel")).not.toBeInTheDocument()
  await userEvent.selectOptions(level, "3")

  await userEvent.click(page.getByTestId("group-outcome-won"))
  await userEvent.click(page.getByTestId("save-play"))

  await page.findByTestIdWith("page-title", "Spirit Island")
  expect(sent[0]).toMatchObject({
    recordingMode: "WINNER_ONLY",
    setup: { adversary: "england", englandLevel: "3", thematicMap: "false" },
    group: { won: true },
    players: [
      { playerId: bencePlayer.id, setup: { spirit: "thunderspeaker" } },
      { playerId: doraPlayer.id, setup: { spirit: "oceansHungryGrasp" } },
    ],
  })
  expect(sent[0].setup).not.toHaveProperty("swedenLevel")
  expect(sent[0].group?.scores).toBeUndefined()
})

test("with points, the group's rows are filled in and the score is worked out", async () => {
  scoreCanBePreviewed()
  const sent = playsCanBeRecorded(spiritPlay())
  await startRecording()
  await userEvent.click(page.getByTestId("variant-adversary-england"))
  await userEvent.selectOptions(page.getByTestId("variant-englandLevel"), "3")
  await userEvent.click(page.getByTestId("change-recording"))
  await userEvent.click(page.getByTestId("recording-mode-POINTS"))

  // The Invader cards are counted differently after a win and after a loss.
  await userEvent.click(page.getByTestId("group-outcome-lost"))
  expect(page.getByTestId("group-score-invaderCards")).toHaveAccessibleName(
    "Invader cards no longer in the deck",
  )
  await userEvent.click(page.getByTestId("group-outcome-won"))
  expect(page.getByTestId("group-score-invaderCards")).toHaveAccessibleName(
    "Invader cards left in the deck",
  )
  await userEvent.type(page.getByTestId("group-score-invaderCards"), "4")
  await userEvent.type(page.getByTestId("group-score-dahan"), "7")
  await userEvent.type(page.getByTestId("group-score-blight"), "5")

  expect(await page.findByTestIdWith("group-total", "Score: 49")).toBeInTheDocument()
  await userEvent.click(page.getByTestId("save-play"))
  await waitFor(() => expect(sent).toHaveLength(1))
  expect(sent[0]).toMatchObject({
    recordingMode: "POINTS",
    group: { won: true, scores: { invaderCards: 4, dahan: 7, blight: 5 } },
  })
})

test("a play's page shows the group's rows, the score and the adversary's level", async () => {
  signedInAs(bence)
  playIs(spiritPlay())
  renderApp(`/plays/${spiritPlay().id}`)

  expect(await page.findByTestId("group-total")).toHaveTextContent("Score: 49")
  expect(page.getByTestId("group-rows")).toHaveTextContent("Invader cards left in the deck")
  expect(page.getByTestIdWith("result-row", "Bence · Thunderspeaker")).toBeInTheDocument()
  expect(page.getByTestId("recorded-by")).toHaveTextContent(
    /^Points, Adversary: England, Level of England: 3\./,
  )
})
