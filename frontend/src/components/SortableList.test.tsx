import { act, render, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { expect, test, vi } from "vitest"
import i18n from "@/i18n"
import { inside, page } from "@/test/queries"
import { sortableListsCanBeUsed } from "@/test/sortable"
import { SortableItem, SortableList } from "./SortableList"

function List({ names, onMove = () => {} }: { names: string[]; onMove?: () => void }) {
  return (
    <SortableList onMove={onMove}>
      {names.map((name, index) => (
        <SortableItem key={name} id={name} index={index} label={name} testId="item">
          {(handle) => (
            <>
              {handle}
              {name}
            </>
          )}
        </SortableItem>
      ))}
    </SortableList>
  )
}

/** The handle of the item with this name. */
function handleOf(name: string) {
  return inside(page.getByTestIdWith("item", name)).getByTestId("move-handle")
}

/** What screen readers are told about every handle: how to move an item from the keyboard. */
function instructions() {
  return document.querySelector('[id^="dnd-kit-description"]')
}

test("says where the item went", async () => {
  sortableListsCanBeUsed()
  const onMove = vi.fn()
  render(<List names={["Anna", "Bence", "Dora"]} onMove={onMove} />)

  handleOf("Anna").focus()
  await userEvent.keyboard(" ")
  await userEvent.keyboard("{ArrowDown}")
  await userEvent.keyboard("{ArrowDown}")
  await userEvent.keyboard(" ")

  await waitFor(() => expect(onMove).toHaveBeenCalledWith(0, 2))
  expect(onMove).toHaveBeenCalledOnce()
})

test("tells screen readers how to move an item, in the current language", async () => {
  sortableListsCanBeUsed()
  render(<List names={["Anna", "Bence"]} />)
  await waitFor(() => expect(instructions()).toHaveTextContent(/^To move it, press Space\./))

  await act(() => i18n.changeLanguage("nl"))

  expect(handleOf("Anna")).toHaveAccessibleName("Anna verplaatsen")
  expect(handleOf("Anna")).toHaveAttribute("aria-roledescription", "versleepbaar")
  await waitFor(() =>
    expect(instructions()).toHaveTextContent(/^Druk op de spatiebalk om te verplaatsen\./),
  )
})
