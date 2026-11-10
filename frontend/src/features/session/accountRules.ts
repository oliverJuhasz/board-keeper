import { z } from "zod"

// The same rules the backend checks (AccountRules.java). Each message is the name of a text
// under "validation" in the translation files.

export const username = z
  .string()
  .trim()
  .regex(/^[\p{L}\p{N}._-]{3,30}$/u, "validation.usernameInvalid")

export const newPassword = z
  .string()
  .min(16, "validation.passwordTooShort")
  .max(200, "validation.passwordTooLong")

export const playerName = z
  .string()
  .trim()
  .min(1, "validation.required")
  .max(50, "validation.nameTooLong")

export const required = z.string().min(1, "validation.required")
