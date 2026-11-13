import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { KeyRoundIcon } from "lucide-react"
import { useCallback } from "react"
import { Controller, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api, sessionKey } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { PageMessage } from "@/components/PageMessage"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { required } from "./accountRules"
import { usePasskeySignIn } from "./passkeys"

// What the form holds and the rules it must meet. Zod checks them, and TypeScript takes the
// form's type from the same description.
const signInForm = z.object({
  username: required,
  password: required,
  staySignedIn: z.boolean(),
})

export function SignInPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const setup = api.useQuery("get", "/api/setup")

  // React Hook Form keeps track of what is typed and of the errors, without redrawing the
  // whole form on every keystroke.
  const form = useForm({
    resolver: zodResolver(signInForm),
    defaultValues: { username: "", password: "", staySignedIn: true },
  })

  const signIn = api.useMutation("post", "/api/session", {
    // Store the answer as "who is signed in". RedirectIfSignedIn, around this page, then moves on.
    onSuccess: (session) => queryClient.setQueryData(sessionKey, session),
  })
  // useCallback keeps the same function from one drawing of the page to the next, so the hook
  // doesn't start its passkey suggestions over every time.
  const staySignedIn = useCallback(() => form.getValues("staySignedIn"), [form])
  const passkey = usePasskeySignIn(staySignedIn)

  if (setup.data?.required) {
    return (
      <PageMessage title={t("signIn.setupNeeded.title")}>
        {t("signIn.setupNeeded.text")}
      </PageMessage>
    )
  }

  return (
    <form onSubmit={form.handleSubmit((values) => signIn.mutate({ body: values }))} noValidate>
      <FieldGroup>
        <h1 className="font-heading text-xl font-semibold">{t("signIn.title")}</h1>
        <FormProblem error={signIn.error ?? passkey.error} />
        <TextField
          label={t("signIn.username")}
          // "webauthn" lets the browser suggest this site's passkeys here, next to usernames.
          autoComplete={passkey.offered ? "username webauthn" : "username"}
          autoCapitalize="none"
          autoFocus
          error={form.formState.errors.username}
          {...form.register("username")}
        />
        <TextField
          label={t("signIn.password")}
          type="password"
          autoComplete="current-password"
          error={form.formState.errors.password}
          {...form.register("password")}
        />
        {/* The checkbox isn't a plain input, so Controller connects it to the form. */}
        <Controller
          control={form.control}
          name="staySignedIn"
          render={({ field }) => (
            <Field orientation="horizontal">
              <Checkbox
                id="stay-signed-in"
                checked={field.value}
                onCheckedChange={(checked) => field.onChange(checked)}
              />
              <FieldContent>
                <FieldLabel htmlFor="stay-signed-in">{t("signIn.staySignedIn")}</FieldLabel>
                <FieldDescription>{t("signIn.staySignedInHint")}</FieldDescription>
              </FieldContent>
            </Field>
          )}
        />
        <Button type="submit" disabled={signIn.isPending}>
          {t("signIn.submit")}
        </Button>
        {passkey.offered && (
          <Button
            type="button"
            variant="outline"
            disabled={passkey.pending}
            onClick={() => void passkey.signIn()}
          >
            <KeyRoundIcon />
            {t("signIn.withPasskey")}
          </Button>
        )}
      </FieldGroup>
    </form>
  )
}
