import { vi } from "vitest"

/**
 * Lets a test move the items of a `SortableList` from the keyboard.
 *
 * jsdom builds the page but doesn't lay it out, so every element measures 0 by 0, and dnd-kit,
 * which decides from the items' sizes and places where a keyboard move goes, finds nowhere to go.
 * Here each list item measures 40 pixels high and starts 50 pixels below the one before it, and
 * everything else is a large box around them. jsdom also lacks a few browser features that
 * dnd-kit uses for animations and for following the page; these stand in for them and do nothing.
 */
export function sortableListsCanBeUsed() {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function (this: Element) {
    const item = this.closest("li")
    if (!item?.parentElement) {
      return rectangle(0, 2000, 1000)
    }
    const index = Array.from(item.parentElement.children).indexOf(item)
    return rectangle(100 + index * 50, 40, 600)
  })
  vi.stubGlobal("IntersectionObserver", DoNothingObserver)
  vi.stubGlobal("ResizeObserver", DoNothingObserver)
  vi.stubGlobal("matchMedia", () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }))
  Object.assign(document, { getAnimations: () => [] })
  Object.assign(Element.prototype, {
    getAnimations: () => [],
    animate: () => ({ finished: Promise.resolve(), cancel() {}, finish() {} }),
  })
}

function rectangle(top: number, height: number, width: number): DOMRect {
  return {
    x: 0,
    y: top,
    top,
    left: 0,
    right: width,
    bottom: top + height,
    width,
    height,
    toJSON: () => ({}),
  }
}

class DoNothingObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return []
  }
}

/**
 * Everything dnd-kit tells screen readers from now on, in order. It says it in a "live region",
 * an element whose changes screen readers read out, which it adds to the page once a list has
 * an item: call this when the list is on the page.
 */
export function announcements() {
  const said: string[] = []
  const region = document.querySelector('[id^="dnd-kit-announcement"]')
  if (!region) {
    throw new Error("dnd-kit has no live region yet")
  }
  new MutationObserver(() => {
    if (region.textContent && said.at(-1) !== region.textContent) {
      said.push(region.textContent)
    }
  }).observe(region, { characterData: true, childList: true, subtree: true })
  return said
}
