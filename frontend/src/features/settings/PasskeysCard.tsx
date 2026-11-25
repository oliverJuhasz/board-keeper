import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { KeyRoundIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useRef, useState, type ComponentProps } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, fetchClient } from "@/api/client"
import { ConfirmDialog } from "@/components/ConfirmDialog"
import { FormProblem } from "@/components/FormProblem"
import { QueryState } from "@/components/QueryState"
import { TextField } from "@/components/TextField"
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
import { FieldGroup } from "@/components/ui/field"
import { passkeyLabel } from "@/features/session/accountRules"
import { browserHasPasskeys, makePasskey, wasCancelled } from "@/features/session/passkeys"
import { formatDate } from "@/lib/dates"

type Passkey = { id: string; label: string; created?: string; lastUsed?: string }

const passkeysKey = ["get", "/api/account/passkeys"]

/**
 * The signed-in Account's passkeys: each one a phone, computer or security key that can sign in
 * without the password. The password keeps working too.
 */
export function PasskeysCard() {
  const { t } = useTranslation()
  const passkeys = api.useQuery("get", "/api/account/passkeys")
  const [adding, setAdding] = useState(false)
  // The button that opens the dialog, to give focus back to once it closes.
  const addButton = useRef<HTMLButtonElement>(null)
  const [removing, setRemoving] = useState<Passkey>()
  const queryClient = useQueryClient()
  const remove = api.useMutation("delete", "/api/account/passkeys/{id}", {
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: passkeysKey })
      setRemoving(undefined)
    },
  })

  return (
    <Card data-testid="passkeys">
      <CardHeader>
        <CardTitle>{t("settings.passkeys.title")}</CardTitle>
        <CardDescription>{t("settings.passkeys.description")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <QueryState
          queries={[passkeys]}
          loading={t("settings.passkeys.loading")}
          failed={t("settings.passkeys.loadFailed")}
        >
          {() =>
            passkeys.data?.length === 0 ? (
              <p className="text-sm text-muted-foreground" data-testid="no-passkeys">
                {t("settings.passkeys.none")}
              </p>
            ) : (
              <ul className="divide-y">
                {passkeys.data?.map((passkey) => (
                  <li
                    key={passkey.id}
                    className="flex items-center gap-3 py-2"
                    data-testid="passkey"
                  >
                    <KeyRoundIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{passkey.label}</p>
                      <p className="text-sm text-muted-foreground" data-testid="passkey-details">
                        {[
                          passkey.created &&
                            t("settings.passkeys.added", { date: formatDate(passkey.created) }),
                          passkey.lastUsed
                            ? t("settings.passkeys.lastUsed", {
                                date: formatDate(passkey.lastUsed),
                              })
                            : t("settings.passkeys.neverUsed"),
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t("settings.passkeys.remove", { label: passkey.label })}
                      data-testid="remove-passkey"
                      onClick={() => setRemoving(passkey)}
                    >
                      <Trash2Icon />
                    </Button>
                  </li>
                ))}
              </ul>
            )
          }
        </QueryState>
        {/* A browser without passkeys can still see and remove the ones made elsewhere. */}
        {browserHasPasskeys() ? (
          <Button
            ref={addButton}
            variant="outline"
            onClick={() => setAdding(true)}
            data-testid="add-passkey"
          >
            <PlusIcon />
            {t("settings.passkeys.add")}
          </Button>
        ) : (
          <p className="text-sm text-muted-foreground">{t("settings.passkeys.notInThisBrowser")}</p>
        )}
      </CardContent>

      {adding && <AddPasskeyDialog onClose={() => setAdding(false)} finalFocus={addButton} />}
      {removing && (
        <ConfirmDialog
          title={t("settings.passkeys.removeDialog.title", { label: removing.label })}
          description={t("settings.passkeys.removeDialog.description")}
          confirm={t("settings.passkeys.removeDialog.confirm")}
          onConfirm={() => remove.mutate({ params: { path: { id: removing.id } } })}
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setRemoving(undefined)}
        />
      )}
    </Card>
  )
}

const labelForm = z.object({ label: passkeyLabel })

/**
 * Asks for a name, then has the browser make the passkey. The name only helps its owner tell
 * their passkeys apart, so the form suggests none.
 *
 * @param finalFocus where focus goes once the dialog has closed, such as the button that opened
 *   it. "Dialogs" in docs/development.md says when a dialog needs this.
 */
function AddPasskeyDialog({
  onClose,
  finalFocus,
}: {
  onClose: () => void
  finalFocus?: ComponentProps<typeof DialogContent>["finalFocus"]
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm({ resolver: zodResolver(labelForm), defaultValues: { label: "" } })

  // Three steps in one: options from the server, the passkey from the browser, and the
  // browser's answer back to the server. A plain TanStack mutation runs all three.
  const add = useMutation({
    mutationFn: async (label: string) => {
      const options = await fetchClient.POST("/api/account/passkeys/options")
      if (options.error) {
        throw options.error
      }
      const credential = await makePasskey(options.data)
      const added = await fetchClient.POST("/api/account/passkeys", {
        body: { label, credential },
      })
      if (added.error) {
        throw added.error
      }
      return added.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: passkeysKey })
      onClose()
    },
  })
  // Cancelling on the phone is a change of mind, not an error.
  const error = wasCancelled(add.error)
    ? undefined
    : add.error instanceof DOMException
      ? { code: "passkeys.notAdded" }
      : add.error

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent finalFocus={finalFocus} data-testid="add-passkey-dialog">
        <form
          onSubmit={(event) => void form.handleSubmit((values) => add.mutate(values.label))(event)}
          noValidate
        >
          <FieldGroup>
            <DialogHeader>
              <DialogTitle>{t("settings.passkeys.addDialog.title")}</DialogTitle>
              <DialogDescription>{t("settings.passkeys.addDialog.description")}</DialogDescription>
            </DialogHeader>
            <FormProblem error={error} />
            <TextField
              label={t("settings.passkeys.addDialog.label")}
              hint={t("settings.passkeys.addDialog.labelHint")}
              autoComplete="off"
              autoFocus
              error={form.formState.errors.label}
              data-testid="passkey-label"
              {...form.register("label")}
            />
            <DialogFooter>
              <DialogClose render={<Button type="button" variant="outline" />}>
                {t("common.cancel")}
              </DialogClose>
              <Button type="submit" disabled={add.isPending} data-testid="save-passkey">
                {t("settings.passkeys.addDialog.submit")}
              </Button>
            </DialogFooter>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}
