import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router"
import { z } from "zod"
import { api, sessionKey } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { PageMessage } from "@/components/PageMessage"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { newPassword } from "./accountRules"

const resetForm = z.object({ password: newPassword })

/** The page behind a Reset Link: someone who forgot their password chooses a new one here. */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const token = useParams().token ?? ""

  const reset = api.useQuery("get", "/api/password-reset/{token}", { params: { path: { token } } })
  const form = useForm({ resolver: zodResolver(resetForm), defaultValues: { password: "" } })
  const save = api.useMutation("post", "/api/password-reset/{token}", {
    onSuccess: (session) => {
      queryClient.clear()
      queryClient.setQueryData(sessionKey, session)
      void navigate("/", { replace: true })
    },
  })

  if (reset.isPending) {
    return <p className="text-muted-foreground">{t("app.loading")}</p>
  }
  if (reset.isError) {
    return (
      <PageMessage title={t("resetPassword.notValid.title")}>
        {t("resetPassword.notValid.text")}
      </PageMessage>
    )
  }

  return (
    <form
      onSubmit={form.handleSubmit((values) =>
        save.mutate({ params: { path: { token } }, body: values }),
      )}
      noValidate
    >
      <FieldGroup>
        <div className="space-y-1">
          <h1 className="font-heading text-xl font-semibold">{t("resetPassword.title")}</h1>
          <p className="text-muted-foreground">
            {t("resetPassword.intro", { username: reset.data.username })}
          </p>
        </div>
        <FormProblem error={save.error} />
        <TextField
          label={t("resetPassword.password")}
          hint={t("setup.passwordHint")}
          type="password"
          autoComplete="new-password"
          autoFocus
          error={form.formState.errors.password}
          data-testid="password"
          {...form.register("password")}
        />
        <Button type="submit" disabled={save.isPending} data-testid="save-password">
          {t("resetPassword.submit")}
        </Button>
      </FieldGroup>
    </form>
  )
}
