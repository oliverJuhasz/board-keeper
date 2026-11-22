import type { Locator, Page } from "@playwright/test"
import { ADMIN } from "../support/cast.ts"
import { expect, test } from "../support/fixtures.ts"
import {
  addPlayers,
  fillScorePad,
  playerRow,
  scorePadPlayers,
  startRecording,
} from "../support/playForm.ts"

// Journey 7. The test starts as the Admin, whose Player is Dora.
test("the Players of a Play are dragged into another order, which the score pad and the saved Play follow", async ({
  page,
}, testInfo) => {
  // Calico has a score pad with a column for each Player.
  await startRecording(page, "Calico")
  await addPlayers(page, ["Anna", "Bence"])
  await expect(page.getByTestId("player-row")).toHaveText([/Dora/, /Anna/, /Bence/])

  await test.step("Bence is dragged to the top", async () => {
    const from = playerRow(page, "Bence").getByTestId("move-handle")
    const to = playerRow(page, ADMIN.player).getByTestId("move-handle")
    // On a phone with a finger, as people do there, and elsewhere with the mouse.
    await drag(page, from, to, { finger: testInfo.project.use.hasTouch === true })
    await expect(page.getByTestId("player-row")).toHaveText([/Bence/, /Dora/, /Anna/])
    await expect(scorePadPlayers(page)).toHaveText(["Bence", ADMIN.player, "Anna"])
  })

  await test.step("the Play keeps the order", async () => {
    await fillScorePad(page, {
      Bence: { designGoals: 20, cats: 15, buttons: 9 },
      [ADMIN.player]: { designGoals: 18, cats: 12, buttons: 6 },
      Anna: { designGoals: 22, cats: 9, buttons: 3 },
    })
    await page.getByTestId("save-play").click()
    await page.getByTestId("play-actions").click()
    await page.getByTestId("edit-play").click()
    await expect(page.getByTestId("player-row")).toHaveText([/Bence/, /Dora/, /Anna/])
    await expect(scorePadPlayers(page)).toHaveText(["Bence", ADMIN.player, "Anna"])
  })
})

/** The middle of an element, where a mouse or a finger takes hold of it. */
async function middleOf(element: Locator) {
  const box = await element.boundingBox()
  if (!box) {
    throw new Error("The element isn't on the screen.")
  }
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

/** Drags an element onto another, with a finger or the mouse. */
async function drag(page: Page, from: Locator, to: Locator, { finger }: { finger: boolean }) {
  return finger ? await dragWithFinger(page, from, to) : await dragWithMouse(page, from, to)
}

/** Drags with the mouse, in small steps, as dnd-kit follows a pointer that moves. */
async function dragWithMouse(page: Page, from: Locator, to: Locator) {
  const start = await middleOf(from)
  const end = await middleOf(to)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await page.mouse.move(end.x, end.y, { steps: 10 })
  await page.mouse.up()
}

/**
 * Drags with a finger. Playwright can tap but not drag on a touch screen, so this asks Chromium
 * itself, through its DevTools protocol (the interface Chromium's developer tools use), to
 * report a finger touching, moving and letting go.
 */
async function dragWithFinger(page: Page, from: Locator, to: Locator) {
  const start = await middleOf(from)
  const end = await middleOf(to)
  const chromium = await page.context().newCDPSession(page)
  await chromium.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [start] })
  const steps = 10
  for (let step = 1; step <= steps; step++) {
    const point = {
      x: start.x + ((end.x - start.x) * step) / steps,
      y: start.y + ((end.y - start.y) * step) / steps,
    }
    await chromium.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [point] })
  }
  await chromium.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] })
  await chromium.detach()
}
