import type { VariantProps } from "class-variance-authority"
import { cn } from "cn"
import { Link, type LinkProps } from "react-router"
import { buttonVariants } from "@/components/ui/button"

/**
 * A link that looks like a button. Use it for going to another page, and Button for doing
 * something on this one: a real link can be opened in a new tab, and a screen reader
 * announces it as a link.
 */
export function LinkButton({
  className,
  variant = "outline",
  size,
  ...link
}: LinkProps & VariantProps<typeof buttonVariants>) {
  // cn() drops the classes that a later class overrides, such as the transparent border that
  // every button starts with and the "outline" variant replaces.
  return <Link className={cn(buttonVariants({ variant, size, className }))} {...link} />
}
