import { Skeleton } from "@/components/ui/skeleton"

/** Grey placeholder rows, shown while a list is loading. */
export function LoadingRows({ label }: { label: string }) {
  return (
    <div role="status" aria-label={label} className="space-y-2" data-testid="loading">
      <Skeleton className="h-5 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
    </div>
  )
}
