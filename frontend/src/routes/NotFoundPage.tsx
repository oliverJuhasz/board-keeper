import { Link } from "react-router"

export function NotFoundPage() {
  return (
    <div className="space-y-2">
      <h1 className="font-heading text-xl font-semibold">Page not found</h1>
      <p className="text-muted-foreground">
        There is nothing at this address.{" "}
        <Link to="/" className="text-foreground underline underline-offset-4">
          Go to the start page
        </Link>
      </p>
    </div>
  )
}
