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
import { currentLanguage } from "@/i18n"
import { newPassword, username } from "./accountRules"

const registerForm = z.object({ username, password: newPassword })

/** The page behind an Invite's link: the invited person creates their Account here. */
export function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // The ":token" part of the address, as declared for this page in router.tsx.
  const token = useParams().token ?? ""

  const registration = api.useQuery("get", "/api/registration/{token}", {
    params: { path: { token } },
  })
  const form = useForm({
    resolver: zodResolver(registerForm),
    defaultValues: { username: "", password: "" },
  })
  const register = api.useMutation("post", "/api/registration/{token}", {
    onSuccess: (session) => {
      queryClient.clear()
      queryClient.setQueryData(sessionKey, session)
      void navigate("/", { replace: true })
    },
  })

  if (registration.isPending) {
    return <p className="text-muted-foreground">{t("app.loading")}</p>
  }
  if (registration.isError) {
    return (
      <PageMessage title={t("register.notValid.title")}>{t("register.notValid.text")}</PageMessage>
    )
  }

  return (
    <form
      onSubmit={form.handleSubmit((values) =>
        register.mutate({
          params: { path: { token } },
          body: { ...values, language: currentLanguage() },
        }),
      )}
      noValidate
    >
      <FieldGroup>
        <div className="space-y-1">
          <h1 className="font-heading text-xl font-semibold" data-testid="page-title">
            {t("register.title")}
          </h1>
          <p className="text-muted-foreground">
            {t("register.intro", { name: registration.data.playerName })}
          </p>
        </div>
        <FormProblem error={register.error} />
        <TextField
          label={t("setup.username")}
          hint={t("setup.usernameHint")}
          autoComplete="username"
          autoCapitalize="none"
          autoFocus
          error={form.formState.errors.username}
          data-testid="username"
          {...form.register("username")}
        />
        <TextField
          label={t("setup.password")}
          hint={t("setup.passwordHint")}
          type="password"
          autoComplete="new-password"
          error={form.formState.errors.password}
          data-testid="password"
          {...form.register("password")}
        />
        <Button type="submit" disabled={register.isPending} data-testid="register">
          {t("register.submit")}
        </Button>
      </FieldGroup>
    </form>
  )
}
