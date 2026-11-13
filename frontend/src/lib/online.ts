import { onlineManager } from "@tanstack/react-query"
import { useSyncExternalStore } from "react"

/**
 * Whether the device has a connection, as the browser sees it. TanStack Query's onlineManager
 * follows the browser's "online" and "offline" events; useSyncExternalStore redraws the
 * component whenever that changes.
 *
 * "Online" only means there is a network. Whether Board Keeper can be reached shows when a
 * request is made.
 */
export function useOnline() {
  return useSyncExternalStore(
    (changed) => onlineManager.subscribe(changed),
    () => onlineManager.isOnline(),
  )
}
