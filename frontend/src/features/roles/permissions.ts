import type { Permission } from "@/api/client"

// Whether each Permission is for Admins only: those belong to the Admin Role and cannot be
// put into any other Role. The backend enforces this (Permission.java); the list here only
// decides what the Role dialog offers. Because every Permission must appear, TypeScript
// complains when the API gains one that is missing here.
const adminOnly: Record<Permission, boolean> = {
  CREATE_PLAYERS: false,
  EDIT_PLAYERS: false,
  REMOVE_PLAYERS: false,
  CREATE_GAMES: false,
  EDIT_GAMES: false,
  REMOVE_GAMES: false,
  CREATE_PLAYS: false,
  CHANGE_PLAYS_RECORDED: false,
  CHANGE_PLAYS_TAKEN_PART_IN: false,
  CHANGE_ANY_PLAY: false,
  USE_API_KEYS: false,
  MANAGE_INVITES: true,
  MANAGE_ACCOUNTS: true,
  MANAGE_ROLES: true,
  RECALCULATE_PLAYS: true,
}

/** The Permissions a Role can be given, in the order the interface lists them. */
export const assignablePermissions = (Object.keys(adminOnly) as Permission[]).filter(
  (permission) => !adminOnly[permission],
)
