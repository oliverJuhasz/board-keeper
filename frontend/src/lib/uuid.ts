/**
 * A new UUID of version 7, like the ids the database makes (uuidv7() in PostgreSQL). Its first
 * 48 bits are the time in milliseconds, so ids made later sort later; the play history relies
 * on that to put the Plays of a day in the order they were recorded. The rest is random.
 *
 * The browser's crypto.randomUUID() makes version 4 ids, which are random throughout.
 */
export function uuidv7(now = Date.now()) {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  // The time, most significant byte first, in bytes 0 to 5.
  for (let index = 0; index < 6; index++) {
    bytes[index] = Math.floor(now / 2 ** (8 * (5 - index))) % 256
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70 // the version, 7, in the high half of byte 6
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // the variant, binary 10, in the top bits of byte 8
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("")
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
