import { expect, test } from "vitest"
import { uuidv7 } from "./uuid"

test("makes a version 7 UUID that starts with the time", () => {
  const id = uuidv7(Date.UTC(2026, 2, 14, 12, 0, 0))

  expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  // 2026-03-14T12:00:00Z is 1773489600000 milliseconds after 1970, 0x019ceb9ffa00.
  expect(id.replaceAll("-", "").slice(0, 12)).toBe(
    Date.UTC(2026, 2, 14, 12).toString(16).padStart(12, "0"),
  )
})

test("ids made later sort later", () => {
  const earlier = uuidv7(Date.UTC(2026, 2, 14, 12, 0, 0))
  const later = uuidv7(Date.UTC(2026, 2, 14, 12, 0, 1))

  expect([later, earlier].sort()).toEqual([earlier, later])
})

test("no two are the same", () => {
  const now = Date.now()
  expect(uuidv7(now)).not.toBe(uuidv7(now))
})
