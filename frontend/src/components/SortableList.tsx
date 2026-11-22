import { RestrictToVerticalAxis } from "@dnd-kit/abstract/modifiers"
import { Accessibility, defaultPreset, type Draggable } from "@dnd-kit/dom"
import { DragDropProvider, KeyboardSensor, PointerSensor } from "@dnd-kit/react"
import { isSortable, useSortable } from "@dnd-kit/react/sortable"
import type { TFunction } from "i18next"
import { GripVerticalIcon } from "lucide-react"
import { useMemo, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

// dnd-kit is set up by handing its manager plugins, sensors and modifiers:
// - sensors notice the start of a move, here by a pointer (mouse, finger or pen) or the keyboard;
// - modifiers change how the dragged item follows the pointer, here only up and down;
// - plugins add everything else, such as what screen readers are told.
const sensors = [
  // Only the handle starts a move, and on a touch screen it doesn't scroll the page (touch-none).
  // So a finger on it can start a move at once, as a mouse does, without first being held still.
  PointerSensor.configure({ activationConstraints: () => undefined }),
  KeyboardSensor,
]
const modifiers = [RestrictToVerticalAxis]

/** What each item of the list tells dnd-kit about itself, for screen readers. */
type ItemData = { label: string }

/** What can be said to screen readers. dnd-kit doesn't export this type by name. */
type AccessibilityOptions = NonNullable<ConstructorParameters<typeof Accessibility>[1]>

/** What screen readers are told about moving an item: how to, and where the item is. */
function accessibilityIn(t: TFunction): AccessibilityOptions {
  /** Says where an item is, such as "number 2 of 3". */
  const where = (source: Draggable | null, number: number) => ({
    name: (source?.data as ItemData | undefined)?.label,
    number: number + 1,
    count: [...(source?.manager?.registry.droppables ?? [])].filter((droppable) =>
      isSortable(droppable),
    ).length,
  })
  // Where the item was when a screen reader was last told, so that it is told only of a change.
  let announcedIndex: number | undefined

  return {
    screenReaderInstructions: { draggable: t("sortable.instructions") },
    announcements: {
      dragstart: ({ operation: { source } }) => {
        if (!isSortable(source)) {
          return undefined
        }
        announcedIndex = source.index
        return t("sortable.pickedUp", where(source, source.index))
      },
      // Once the list has made room for the item, the item is over its own new place.
      dragover: ({ operation: { source, target } }) => {
        if (!isSortable(source) || target?.id !== source.id || source.index === announcedIndex) {
          return undefined
        }
        announcedIndex = source.index
        return t("sortable.moved", where(source, source.index))
      },
      dragend: ({ operation: { source }, canceled }) => {
        if (!isSortable(source)) {
          return undefined
        }
        return canceled
          ? t("sortable.cancelled", where(source, source.initialIndex))
          : t("sortable.dropped", where(source, source.index))
      },
    },
  }
}

/**
 * A list whose items can be put in another order. Each item has a handle that can be dragged
 * with a mouse or a finger. From the keyboard, Space on the handle picks the item up, the arrow
 * keys move it, Space puts it down, and Escape puts it back. Screen readers are told how, and
 * what happens.
 *
 * Only the handle starts a move, so the fields in an item keep working as usual. While an item is
 * dragged, the list shows where it would go; `onMove` is called once it is put down somewhere new.
 */
export function SortableList({
  onMove,
  className,
  children,
}: {
  /** Called with where the item was and where it is now, counted from 0. */
  onMove: (from: number, to: number) => void
  className?: string
  /** The items, as `SortableItem`s. */
  children: ReactNode
}) {
  const { t, i18n } = useTranslation()
  // Kept between renders: dnd-kit sets up its plugins again whenever it is handed a new list.
  const plugins = useMemo(
    () => [
      ...defaultPreset.plugins,
      // The same plugin again, now with texts of our own. dnd-kit keeps the last of the two.
      Accessibility.configure(accessibilityIn(t)),
    ],
    [t],
  )

  return (
    <DragDropProvider
      // dnd-kit writes the instructions for screen readers once, when it starts, so it is started
      // again in another language. The items are then drawn afresh: what was entered in them is
      // kept by their form, as React Hook Form keeps the values of the Play's form.
      key={i18n.resolvedLanguage}
      plugins={plugins}
      sensors={sensors}
      modifiers={modifiers}
      onDragEnd={({ operation: { source }, canceled }) => {
        // While dragging, dnd-kit moves the item on the page itself. Once it is put down, the
        // list it was drawn from is changed to match. A move that was cancelled is put back by
        // dnd-kit, and the list stays as it was.
        if (!canceled && isSortable(source) && source.index !== source.initialIndex) {
          onMove(source.initialIndex, source.index)
        }
      }}
    >
      <ul className={className}>{children}</ul>
    </DragDropProvider>
  )
}

/**
 * One item of a `SortableList`. It draws itself with `children`, which is handed the item's handle
 * to put where it belongs, such as at the start of the item's first line.
 */
export function SortableItem({
  id,
  index,
  label,
  movable = true,
  className,
  testId,
  children,
}: {
  /** Stays the same while the item moves, such as the id useFieldArray gives each row. */
  id: string
  /** Where the item is now, counted from 0. */
  index: number
  /** What the item is called, such as a Player's name, for the handle and screen readers. */
  label: string
  /** False where moving makes no sense, such as for the only item: the handle is then hidden. */
  movable?: boolean
  className?: string
  /** The item's test id, for the end-to-end tests (ADR 0023). */
  testId?: string
  children: (handle: ReactNode) => ReactNode
}) {
  const { t } = useTranslation()
  // A new object would count as a change on every render.
  const data = useMemo<ItemData>(() => ({ label }), [label])
  const { ref, handleRef, isDragging } = useSortable({ id, index, data, disabled: !movable })

  // The handle is hidden, not left out, while the item can't be moved: an item without a handle
  // would be made one itself, and would keep what makes it one when a handle is added later.
  const handle = (
    <Button
      ref={handleRef}
      type="button"
      variant="ghost"
      size="icon-sm"
      hidden={!movable}
      data-testid="move-handle"
      className="cursor-grab touch-none text-muted-foreground"
      aria-label={t("sortable.move", { name: label })}
      // How screen readers name the handle. dnd-kit would otherwise use an English word.
      aria-roledescription={t("sortable.roleDescription")}
    >
      <GripVerticalIcon />
    </Button>
  )

  return (
    <li ref={ref} data-testid={testId}>
      {/* While it is dragged, dnd-kit lifts the item above the page and takes away its own
          background, so the look is drawn inside it. */}
      <div
        className={cn(
          className,
          isDragging && "rounded-lg bg-background shadow-lg ring-1 ring-foreground/10",
        )}
      >
        {children(handle)}
      </div>
    </li>
  )
}
