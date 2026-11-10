import type { Permission } from "@/api/client"

/** Every Permission, in the order the interface lists them. */
export const allPermissions: Permission[] = [
  "CREATE_PLAYERS",
  "EDIT_PLAYERS",
  "REMOVE_PLAYERS",
  "CREATE_PLAYS",
  "MANAGE_INVITES",
  "MANAGE_ACCOUNTS",
  "MANAGE_ROLES",
]
