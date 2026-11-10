import type { TFunction } from "i18next"
import type { Role } from "@/api/client"

/** A Role's name: translated for the two built-in Roles, as typed by an Admin for the others. */
export function roleName(t: TFunction, role: Role) {
  return role.builtIn ? t(`roles.builtIn.${role.builtIn}`) : role.name
}
