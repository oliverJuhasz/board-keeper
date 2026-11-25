import { ExternalLinkIcon } from "lucide-react"
import { useState, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { api, type About } from "@/api/client"
import { QueryState } from "@/components/QueryState"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { appCommit, reloadIntoNewestVersion } from "@/lib/appUpdate"
import { formatDate } from "@/lib/dates"

/**
 * About Board Keeper: which version is running, a word from its maker, where its source code
 * is, and its licence. The licence, the AGPL, asks that everyone who uses a copy can get that
 * copy's source (ADR 0016), so the links come from the server's settings, not from here.
 */
export function AboutPage() {
  const { t } = useTranslation()
  // Asked again each time the page is opened: the server may have been updated since.
  const about = api.useQuery("get", "/api/about")

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-xl font-semibold" data-testid="page-title">
        {t("about.title")}
      </h1>

      <QueryState queries={[about]} loading={t("about.loading")} failed={t("about.loadFailed")}>
        {() => about.data && <VersionCard about={about.data} />}
      </QueryState>

      {/* <figure> ties a quotation to who said it, also for a screen reader. */}
      <figure className="space-y-1 border-l-2 pl-4">
        <blockquote className="text-base" data-testid="about-message">
          {t("about.message")}
        </blockquote>
        <figcaption className="text-muted-foreground" data-testid="about-signature">
          — {t("about.signature")}
        </figcaption>
      </figure>

      {about.data && <SourceCard about={about.data} />}

      <Card>
        <CardHeader>
          <CardTitle>{t("about.licence.title")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p data-testid="licence">{t("about.licence.text")}</p>
          <ul className="space-y-1">
            <li>
              <ExternalLink href="https://www.gnu.org/licenses/agpl-3.0.html" testId="licence-text">
                {t("about.licence.full")}
              </ExternalLink>
            </li>
            <li>
              {/* Written by the build, from the packages in it (vite-plugins/thirdPartyLicenses.ts). */}
              <ExternalLink href="/third-party-licenses.txt" testId="library-licences">
                {t("about.licence.thirdParty")}
              </ExternalLink>
            </li>
          </ul>
          <p className="text-muted-foreground">{t("about.licence.trademarks")}</p>
        </CardContent>
      </Card>
    </div>
  )
}

/** The name of the image of a commit, as the self-hosting guide has it: "sha-1a2b3c4". */
function imageTag(commit: string) {
  return `sha-${commit.slice(0, 7)}`
}

/**
 * Which version the server runs. A release shows its number. Anything else is a Snapshot, and
 * shows what helps to tell which one: its commit, that commit's day, and how far it is past the
 * newest release (ADR 0018).
 */
function VersionCard({ about }: { about: About }) {
  const { t } = useTranslation()
  const [reloading, setReloading] = useState(false)

  const title = about.version
    ? t("about.version.release", { version: about.version })
    : about.commit
      ? t("about.version.snapshot")
      : t("about.version.unknown")

  const details = about.commit
    ? [
        imageTag(about.commit),
        about.committedAt && formatDate(about.committedAt),
        !about.version &&
          (about.latestRelease
            ? t("about.version.changes", {
                count: about.changesSinceRelease ?? 0,
                release: about.latestRelease,
              })
            : t("about.version.noRelease")),
        about.uncommittedChanges && t("about.version.uncommitted"),
      ]
        .filter(Boolean)
        .join(" · ")
    : t("about.version.unknownHint")

  // The app on this device is built from one commit and the server from another: the server
  // was updated, and this device still shows the app as it was before.
  const device = appCommit()
  const deviceIsBehind =
    device !== undefined && about.commit !== undefined && device !== about.commit

  return (
    <Card data-testid="version">
      <CardHeader>
        <CardTitle data-testid="version-title">{title}</CardTitle>
        <CardDescription data-testid="version-details">{details}</CardDescription>
      </CardHeader>
      {(about.releaseNotes || deviceIsBehind) && (
        <CardContent className="space-y-3">
          {about.releaseNotes && (
            <p>
              <ExternalLink href={about.releaseNotes} testId="whats-new">
                {t("about.version.whatsNew", { version: about.version })}
              </ExternalLink>
            </p>
          )}
          {deviceIsBehind && (
            <div className="flex flex-wrap items-center gap-3">
              <p role="status" className="min-w-48 flex-1" data-testid="device-behind">
                {t("about.version.device", { version: imageTag(device) })}
              </p>
              <Button
                variant="outline"
                size="sm"
                disabled={reloading}
                data-testid="reload-app"
                onClick={() => {
                  setReloading(true)
                  void reloadIntoNewestVersion()
                }}
              >
                {t("app.update.reload")}
              </Button>
            </div>
          )}
        </CardContent>
      )}
    </Card>
  )
}

/** Where this copy's source code is: its repository, and the code of the version that runs. */
function SourceCard({ about }: { about: About }) {
  const { t } = useTranslation()

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("about.source.title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-1">
          {about.repository && (
            <li>
              {/* The address itself is the text, so it is right wherever a copy keeps its code. */}
              <ExternalLink href={about.repository} testId="repository">
                {about.repository.replace(/^https?:\/\//, "")}
              </ExternalLink>
            </li>
          )}
          <li>
            <ExternalLink href={about.sourceCode} testId="source-of-this-version">
              {t("about.source.thisVersion")}
            </ExternalLink>
          </li>
        </ul>
      </CardContent>
    </Card>
  )
}

/**
 * A link that leaves Board Keeper, opened in a new tab so that the app stays where it is. The
 * icon says so before the tap. `rel="noreferrer"` keeps the other site from learning Board
 * Keeper's address.
 */
function ExternalLink({
  href,
  testId,
  children,
}: {
  href: string
  /** The link's test id, for the tests (ADR 0023). */
  testId?: string
  children: ReactNode
}) {
  return (
    <a
      href={href}
      data-testid={testId}
      target="_blank"
      rel="noreferrer"
      className="inline-flex items-center gap-1 break-all underline decoration-muted-foreground/40 underline-offset-4 hover:decoration-foreground"
    >
      {children}
      <ExternalLinkIcon aria-hidden className="size-3.5 shrink-0 text-muted-foreground" />
    </a>
  )
}
