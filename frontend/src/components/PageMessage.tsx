import type { ReactNode } from "react"

/** A page that only has something to say: a title, a text, and maybe a link or a button. */
export function PageMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="space-y-2">
      <h1 className="font-heading text-xl font-semibold" data-testid="page-title">
        {title}
      </h1>
      <div className="space-y-3 text-muted-foreground">{children}</div>
    </div>
  )
}
