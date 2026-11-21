import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import type { NewPlay, Play, PlayPreview, PlayPreviewRequest } from "@/api/client"
import {
  bence,
  bencePlayer,
  doraPlayer,
  gamesAre,
  lastBastion,
  playCanBeEntered,
  playIs,
  signedInAs,
} from "@/test/api"
import { lastBastionModule } from "@/test/gameModules"
import { renderApp } from "@/test/renderApp"
import { server } from "@/test/server"

// A cooperative Game that scores only a victory: Last Bastion's rows are filled in once the
// group has won, and a defeat has no score. Its difficulty is Normal unless said otherwise.

/** A Play of Last Bastion as the server would answer it. */
function bastionPlay(overrides: Partial<Play> = {}): Play {
  return {
    id: "0193a000-0000-7000-8000-0000000000fb",
    game: { id: lastBastion.id, name: lastBastion.name, moduleKey: "last-bastion" },
    gameModuleId: lastBastionModule.id,
    playedOn: "2026-03-14",
    recordingMode: "POINTS",
    setup: { difficulty: "normal" },
    cooperative: true,
    group: { won: false, scores: {} },
    results: [
      {
        player: bencePlayer,
        scores: {},
        setup: { hero: "kazgin" },
        markedWinner: false,
        won: false,
      },
      {
        player: doraPlayer,
        scores: {},
        setup: { hero: "sindara" },
        markedWinner: false,
        won: false,
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

/** Works out the group's score as the module would at Normal difficulty. */
function scoreCanBePreviewed() {
  server.use(
    http.post("*/api/plays/preview", async ({ request }) => {
      const body = (await request.json()) as PlayPreviewRequest
      const rows = body.group?.scores ?? {}
      const total =
        10 +
        (rows.lifeTokens ?? 0) +
        2 * (rows.callToArmsTokens ?? 0) -
        3 * (rows.deadHeroes ?? 0) -
        4 * (rows.graspOfEvilTiles ?? 0)
      const preview: PlayPreview = {
        cooperative: true,
        group: { won: true, scores: rows, total },
        results: body.players.map((player) => ({ playerId: player.playerId, won: true })),
        tieForRecorder: [],
      }
      return HttpResponse.json(preview)
    }),
  )
}

async function startRecording() {
  signedInAs(bence)
  gamesAre([lastBastion])
  playCanBeEntered()
  renderApp(`/plays/new?game=${lastBastion.id}`)
  await userEvent.selectOptions(await screen.findByLabelText("Add a player"), "Dora")
  await userEvent.selectOptions(screen.getByLabelText("Hero of Bence"), "Kazgin")
  await userEvent.selectOptions(screen.getByLabelText("Hero of Dora"), "Sindara")
}

test("a victory is scored from the group's rows, at Normal difficulty unless said otherwise", async () => {
  scoreCanBePreviewed()
  const sent = playsCanBeRecorded(
    bastionPlay({
      group: {
        won: true,
        scores: { lifeTokens: 6, callToArmsTokens: 2, deadHeroes: 1, graspOfEvilTiles: 2 },
        total: 9,
      },
    }),
  )
  await startRecording()

  expect(screen.getByText("Points")).toBeInTheDocument()
  expect(screen.getByRole("radio", { name: "Normal" })).toBeChecked()
  // The rows are there once the group has won.
  expect(screen.queryByLabelText("Life tokens")).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole("radio", { name: "The group won" }))
  await userEvent.type(screen.getByLabelText("Life tokens"), "6")
  await userEvent.type(screen.getByLabelText("Call to Arms tokens"), "2")
  await userEvent.type(screen.getByLabelText("Dead heroes"), "1")
  await userEvent.type(screen.getByLabelText("Bastion tiles in the Grasp of Evil"), "2")

  expect(await screen.findByText("Score: 9")).toBeInTheDocument()
  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  await waitFor(() => expect(sent).toHaveLength(1))
  expect(sent[0]).toMatchObject({
    recordingMode: "POINTS",
    setup: { difficulty: "normal" },
    group: {
      won: true,
      scores: { lifeTokens: 6, callToArmsTokens: 2, deadHeroes: 1, graspOfEvilTiles: 2 },
    },
  })
})

test("a defeat has no rows and no score", async () => {
  const sent = playsCanBeRecorded(bastionPlay())
  await startRecording()

  await userEvent.click(screen.getByRole("radio", { name: "The group won" }))
  await userEvent.type(screen.getByLabelText("Life tokens"), "6")
  await userEvent.click(screen.getByRole("radio", { name: "The group lost" }))
  expect(screen.queryByLabelText("Life tokens")).not.toBeInTheDocument()
  expect(screen.queryByText(/^Score:/)).not.toBeInTheDocument()

  await userEvent.click(screen.getByRole("button", { name: "Record play" }))
  await screen.findByRole("heading", { name: "Last Bastion" })
  expect(sent[0]).toMatchObject({ recordingMode: "POINTS", group: { won: false } })
  expect(sent[0].group?.scores).toBeUndefined()
})
