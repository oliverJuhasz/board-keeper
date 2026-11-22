import { GENERIC_GAME } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"

// Journey 14. The test starts as the Admin.
test("the play history shows 50 Plays at a time, newest first, each once", async ({
  page,
  api,
}) => {
  // 120 Plays are too many to record through the form: they are recorded through the API.
  const newestFirst = await test.step("120 Plays, one a day", async () => {
    const games = await api.get("/api/games")
    const players = await api.get("/api/players")
    const gameId = games.find((game) => game.name === GENERIC_GAME)?.id ?? ""
    const idOf = (name: string) => players.find((player) => player.name === name)?.id ?? ""

    const ids: string[] = []
    for (let daysAgo = 1; daysAgo <= 120; daysAgo++) {
      const play = await api.post("/api/plays", {
        gameId,
        playedOn: dayBefore(daysAgo),
        recordingMode: "POINTS",
        setup: {},
        players: [
          { playerId: idOf("Anna"), scores: { total: 200 + daysAgo }, winner: false },
          { playerId: idOf("Bence"), scores: { total: daysAgo }, winner: false },
        ],
      })
      ids.push(play.id)
    }
    return ids
  })

  const plays = page.getByTestId("play-summary")
  /** Where each Play in the list leads: its page, which has its id in it. */
  const listed = async () =>
    await plays.evaluateAll((links) => links.map((link) => link.getAttribute("href")))
  const pagesOf = (count: number) => newestFirst.slice(0, count).map((id) => `/plays/${id}`)

  await page.goto("/plays")
  await expect(plays).toHaveCount(50)
  expect(await listed()).toEqual(pagesOf(50))

  await page.getByTestId("show-more").click()
  await expect(plays).toHaveCount(100)
  expect(await listed()).toEqual(pagesOf(100))

  await page.getByTestId("show-more").click()
  await expect(plays).toHaveCount(120)
  expect(await listed()).toEqual(pagesOf(120))
  // That was everything.
  await expect(page.getByTestId("show-more")).toBeHidden()
})

/** The day so many days before today, as the API writes a day: 2026-11-21. */
function dayBefore(days: number) {
  const day = new Date()
  day.setUTCDate(day.getUTCDate() - days)
  return day.toISOString().slice(0, 10)
}
