import type { Permission } from "@/api/client"

// Whether each Permission is for Admins only: those belong to the Admin Role and cannot be
// put into any other Role. The backend enforces this (Permission.java); the list here only
// decides what the Role dialog offers. Because every Permission must appear, TypeScript
// complains when the API gains one that is missing here.
const adminOnly: Record<Permission, boolean> = {
  CREATE_PLAYERS: false,
  EDIT_PLAYERS: false,
  REMOVE_PLAYERS: false,
  CREATE_PLAYS: false,
  MANAGE_INVITES: true,
  MANAGE_ACCOUNTS: true,
  MANAGE_ROLES: true,
}

/** The Permissions a Role can be given, in the order the interface lists them. */
export const assignablePermissions = (Object.keys(adminOnly) as Permission[]).filter(
  (permission) => !adminOnly[permission],
)
