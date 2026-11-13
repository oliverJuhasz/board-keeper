import { cn } from "cn"
import { Link, type LinkProps } from "react-router"

/**
 * A link in running text or a table, such as a name that leads to its statistics. A faint
 * underline shows that it is a link, also on a phone, where nothing changes under a finger
 * before the tap; it darkens under the mouse.
 */
export function QuietLink({ className, ...link }: LinkProps) {
  return (
    <Link
      className={cn(
        "underline decoration-muted-foreground/40 underline-offset-4 hover:decoration-foreground",
        className,
      )}
      {...link}
    />
  )
}
