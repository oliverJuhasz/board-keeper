import { useMutation } from "@tanstack/react-query"
import { DownloadIcon } from "lucide-react"
import { useTranslation } from "react-i18next"
import { fetchClient } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

/** The name the server suggests for the file, such as "board-keeper-2026-11-21.json". */
function fileNameOf(response: Response) {
  const disposition = response.headers.get("Content-Disposition") ?? ""
  return /filename="([^"]+)"/.exec(disposition)?.[1] ?? "board-keeper.json"
}

/**
 * Hands a file to the browser to save. A link is made to the file, which is still only in the
 * page's memory, and clicked once; its `download` attribute makes the browser save what it
 * points to under that name, instead of opening it.
 */
function save(file: Blob, name: string) {
  const url = URL.createObjectURL(file)
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  // Once the browser has started saving, the address is no longer needed.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** Saves the Installation's Games, Players and Plays as an Export (ADR 0020). */
export function ExportCard() {
  const { t } = useTranslation()

  // A mutation, although it only reads: it runs when the button is pressed, not when the page
  // opens, and it tells the button when it is busy and what went wrong.
  const download = useMutation({
    mutationFn: async () => {
      // "blob" keeps the answer as the file it is, instead of reading it as JSON.
      const { data, error, response } = await fetchClient.GET("/api/export", { parseAs: "blob" })
      if (error !== undefined) {
        throw error
      }
      save(data, fileNameOf(response))
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("exportAndImport.export.title")}</CardTitle>
        <CardDescription>{t("exportAndImport.export.text")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormProblem error={download.error} />
        <Button onClick={() => download.mutate()} disabled={download.isPending}>
          <DownloadIcon />
          {t("exportAndImport.export.download")}
        </Button>
      </CardContent>
    </Card>
  )
}
