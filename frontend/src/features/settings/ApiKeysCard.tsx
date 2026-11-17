import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { CheckIcon, CopyIcon, KeySquareIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useId, useRef, useState, type ComponentProps } from "react"
import { Controller, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, fetchClient, type ApiKey, type ApiKeyExpiry } from "@/api/client"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { FormProblem } from "@/components/FormProblem"
import { QueryState } from "@/components/QueryState"
import { RadioField } from "@/components/RadioField"
import { TextField } from "@/components/TextField"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { apiKeyName, required } from "@/features/session/accountRules"
import { browserHasPasskeys, confirmWithPasskey, wasCancelled } from "@/features/session/passkeys"
import { useCan, useSession } from "@/features/session/useSession"
import { formatDate } from "@/lib/dates"

const apiKeysKey = ["get", "/api/account/api-keys"]

const expiries: ApiKeyExpiry[] = ["THIRTY_DAYS", "NINETY_DAYS", "ONE_YEAR", "NEVER"]

/**
 * The signed-in Account's API Keys (ADR 0017): each one lets a program, such as a script or an
 * AI agent, use Board Keeper as this Account. Creating them needs the Permission to use them.
 * Without it the Account's API Keys stop working, but it can still see and delete them, so the
 * card stays for as long as there are any.
 */
export function ApiKeysCard() {
  const { t } = useTranslation()
  const allowed = useCan()("USE_API_KEYS")
  const apiKeys = api.useQuery("get", "/api/account/api-keys")
  const [creating, setCreating] = useState(false)
  // The button that opens the dialog, to give focus back to once it closes.
  const createButton = useRef<HTMLButtonElement>(null)
  const [deleting, setDeleting] = useState<ApiKey>()
  const queryClient = useQueryClient()
  const remove = api.useMutation("delete", "/api/account/api-keys/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: apiKeysKey })
      setDeleting(undefined)
    },
  })

  if (!allowed && !apiKeys.data?.length) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("settings.apiKeys.title")}</CardTitle>
        <CardDescription>{t("settings.apiKeys.description")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {!allowed && (
          <Alert>
            <AlertDescription>{t("settings.apiKeys.notAllowed")}</AlertDescription>
          </Alert>
        )}
        <QueryState
          queries={[apiKeys]}
          loading={t("settings.apiKeys.loading")}
          failed={t("settings.apiKeys.loadFailed")}
        >
          {() =>
            apiKeys.data?.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("settings.apiKeys.none")}</p>
            ) : (
              <ul className="divide-y">
                {apiKeys.data?.map((apiKey) => (
                  <li key={apiKey.id} className="flex items-center gap-3 py-2">
                    <KeySquareIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{apiKey.name}</p>
                      <p className="text-sm text-muted-foreground">{describe(apiKey)}</p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t("settings.apiKeys.delete", { name: apiKey.name })}
                      onClick={() => setDeleting(apiKey)}
                    >
                      <Trash2Icon />
                    </Button>
                  </li>
                ))}
              </ul>
            )
          }
        </QueryState>
        {allowed && (
          <Button ref={createButton} variant="outline" onClick={() => setCreating(true)}>
            <PlusIcon />
            {t("settings.apiKeys.create")}
          </Button>
        )}
      </CardContent>

      {creating && (
        <CreateApiKeyDialog onClose={() => setCreating(false)} finalFocus={createButton} />
      )}
      {deleting && (
        <ConfirmDialog
          title={t("settings.apiKeys.deleteDialog.title", { name: deleting.name })}
          description={t("settings.apiKeys.deleteDialog.description")}
          confirm={t("settings.apiKeys.deleteDialog.confirm")}
          onConfirm={() => remove.mutate({ params: { path: { id: deleting.id } } })}
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setDeleting(undefined)}
        />
      )}
    </Card>
  )

  /** When it was made, when it was last used, and until when it works. */
  function describe(apiKey: ApiKey) {
    const expiry = !apiKey.expires
      ? t("settings.apiKeys.neverExpires")
      : new Date(apiKey.expires) <= new Date()
        ? t("settings.apiKeys.expired", { date: formatDate(apiKey.expires) })
        : t("settings.apiKeys.expires", { date: formatDate(apiKey.expires) })
    return [
      t("settings.apiKeys.created", { date: formatDate(apiKey.created) }),
      apiKey.lastUsed
        ? t("settings.apiKeys.lastUsed", { date: formatDate(apiKey.lastUsed) })
        : t("settings.apiKeys.neverUsed"),
      expiry,
    ].join(" · ")
  }
}

const apiKeyForm = z.object({
  name: apiKeyName,
  expiry: z.enum(["THIRTY_DAYS", "NINETY_DAYS", "ONE_YEAR", "NEVER"]),
  password: required,
})

type Confirmation = { password: string } | "passkey"

/**
 * Asks for a name and an expiry, and for the password or a passkey to confirm that it's really
 * the Account's owner: an API Key outlives the session it was made in. Then shows the API Key,
 * once.
 *
 * @param finalFocus where focus goes once the dialog has closed, such as the button that opened
 *   it. "Dialogs" in docs/development.md says when a dialog needs this.
 */
function CreateApiKeyDialog({
  onClose,
  finalFocus,
}: {
  onClose: () => void
  finalFocus?: ComponentProps<typeof DialogContent>["finalFocus"]
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm({
    resolver: zodResolver(apiKeyForm),
    defaultValues: { name: "", expiry: "NINETY_DAYS" as ApiKeyExpiry, password: "" },
  })
  // A passkey can confirm only where the installation and the browser offer passkeys, and the
  // Account has one.
  const passkeysOffered = useSession().data?.passkeys === true && browserHasPasskeys()
  const passkeys = api.useQuery("get", "/api/account/passkeys", {}, { enabled: passkeysOffered })
  const withPasskey = passkeysOffered && (passkeys.data?.length ?? 0) > 0

  const create = useMutation({
    mutationFn: async ({ confirmation }: { confirmation: Confirmation }) => {
      const { name, expiry } = form.getValues()
      let confirmed: { password: string } | { passkey: Record<string, unknown> }
      if (confirmation === "passkey") {
        const options = await fetchClient.POST("/api/account/api-keys/passkey-options")
        if (options.error) {
          throw options.error
        }
        confirmed = { passkey: (await confirmWithPasskey(options.data)) as Record<string, unknown> }
      } else {
        confirmed = confirmation
      }
      const created = await fetchClient.POST("/api/account/api-keys", {
        body: { name, expiry, ...confirmed },
      })
      if (created.error) {
        throw created.error
      }
      return created.data
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: apiKeysKey }),
  })
  // Cancelling on the phone is a change of mind, not an error.
  const error = wasCancelled(create.error)
    ? undefined
    : create.error instanceof DOMException
      ? { code: "passkeys.notConfirmed" }
      : create.error

  async function confirmWithAPasskey() {
    // The password isn't needed this way, so only the name is checked.
    if (await form.trigger("name")) {
      create.mutate({ confirmation: "passkey" })
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent finalFocus={finalFocus}>
        {create.data ? (
          <CreatedApiKey secret={create.data.secret} />
        ) : (
          <form
            onSubmit={(event) =>
              void form.handleSubmit(({ password }) =>
                create.mutate({ confirmation: { password } }),
              )(event)
            }
            noValidate
          >
            <FieldGroup>
              <DialogHeader>
                <DialogTitle>{t("settings.apiKeys.createDialog.title")}</DialogTitle>
                <DialogDescription>
                  {t("settings.apiKeys.createDialog.description")}
                </DialogDescription>
              </DialogHeader>
              <FormProblem error={error} />
              <TextField
                label={t("settings.apiKeys.createDialog.name")}
                hint={t("settings.apiKeys.createDialog.nameHint")}
                autoComplete="off"
                autoFocus
                error={form.formState.errors.name}
                {...form.register("name")}
              />
              {/* The choice isn't a plain input, so Controller connects it to the form. */}
              <Controller
                control={form.control}
                name="expiry"
                render={({ field }) => (
                  <RadioField
                    legend={t("settings.apiKeys.createDialog.expiry")}
                    value={field.value}
                    onChange={field.onChange}
                    options={expiries.map((expiry) => ({
                      value: expiry,
                      label: t(`settings.apiKeys.createDialog.expiries.${expiry}`),
                    }))}
                  />
                )}
              />
              <TextField
                label={t("settings.apiKeys.createDialog.password")}
                hint={t("settings.apiKeys.createDialog.passwordHint")}
                type="password"
                autoComplete="current-password"
                error={form.formState.errors.password}
                {...form.register("password")}
              />
              <DialogFooter>
                <DialogClose render={<Button type="button" variant="outline" />}>
                  {t("common.cancel")}
                </DialogClose>
                {withPasskey && (
                  <Button
                    type="button"
                    variant="outline"
                    disabled={create.isPending}
                    onClick={() => void confirmWithAPasskey()}
                  >
                    {t("settings.apiKeys.createDialog.withPasskey")}
                  </Button>
                )}
                <Button type="submit" disabled={create.isPending}>
                  {t("settings.apiKeys.createDialog.submit")}
                </Button>
              </DialogFooter>
            </FieldGroup>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

/** The new API Key, with a button to copy it. It isn't stored, so this is the only time it shows. */
function CreatedApiKey({ secret }: { secret: string }) {
  const { t } = useTranslation()
  const fieldId = useId()
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(secret)
    setCopied(true)
  }

  return (
    <FieldGroup>
      <DialogHeader>
        <DialogTitle>{t("settings.apiKeys.createdDialog.title")}</DialogTitle>
        <DialogDescription>{t("settings.apiKeys.createdDialog.description")}</DialogDescription>
      </DialogHeader>
      <Field>
        <FieldLabel htmlFor={fieldId}>{t("settings.apiKeys.createdDialog.label")}</FieldLabel>
        <Input
          id={fieldId}
          readOnly
          value={secret}
          className="font-mono"
          onFocus={(event) => event.target.select()}
        />
      </Field>
      <p className="text-sm text-muted-foreground">
        {t("settings.apiKeys.createdDialog.howToUse")}
      </p>
      <DialogFooter>
        <Button variant="outline" onClick={() => void copy()}>
          {copied ? <CheckIcon /> : <CopyIcon />}
          {copied ? t("link.copied") : t("settings.apiKeys.createdDialog.copy")}
        </Button>
        <DialogClose render={<Button />}>{t("link.done")}</DialogClose>
      </DialogFooter>
    </FieldGroup>
  )
}
