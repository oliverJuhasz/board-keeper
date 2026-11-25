import { zodResolver } from "@hookform/resolvers/zod"
import { useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useSearchParams } from "react-router"
import { z } from "zod"
import { api, sessionKey } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { PageMessage } from "@/components/PageMessage"
import { TextField } from "@/components/TextField"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { currentLanguage } from "@/i18n"
import { newPassword, playerName, username } from "./accountRules"

const setupForm = z.object({ playerName, username, password: newPassword })

/** The page behind the one-time link from the server's log: creates the first Admin. */
export function SetupPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const token = useSearchParams()[0].get("token")
  const setup = api.useQuery("get", "/api/setup")

  const form = useForm({
    resolver: zodResolver(setupForm),
    defaultValues: { playerName: "", username: "", password: "" },
  })

  const setUp = api.useMutation("post", "/api/setup", {
    onSuccess: (session) => {
      queryClient.setQueryData(sessionKey, session)
      void navigate("/", { replace: true })
    },
  })

  if (setup.data && !setup.data.required && !setUp.isSuccess) {
    return (
      <PageMessage title={t("setup.done.title")}>
        <p>{t("setup.done.text")}</p>
        <Link to="/sign-in" className="text-foreground underline underline-offset-4">
          {t("setup.done.signIn")}
        </Link>
      </PageMessage>
    )
  }
  if (!token) {
    return <PageMessage title={t("setup.noToken.title")}>{t("setup.noToken.text")}</PageMessage>
  }

  return (
    <form
      onSubmit={form.handleSubmit((values) =>
        setUp.mutate({ body: { ...values, token, language: currentLanguage() } }),
      )}
      noValidate
    >
      <FieldGroup>
        <div className="space-y-1">
          <h1 className="font-heading text-xl font-semibold" data-testid="page-title">
            {t("setup.title")}
          </h1>
          <p className="text-muted-foreground" data-testid="page-description">
            {t("setup.intro")}
          </p>
        </div>
        <FormProblem error={setUp.error} />
        <TextField
          label={t("setup.playerName")}
          hint={t("setup.playerNameHint")}
          autoComplete="name"
          autoFocus
          error={form.formState.errors.playerName}
          data-testid="player-name"
          {...form.register("playerName")}
        />
        <TextField
          label={t("setup.username")}
          hint={t("setup.usernameHint")}
          autoComplete="username"
          autoCapitalize="none"
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
        <Button type="submit" disabled={setUp.isPending} data-testid="set-up">
          {t("setup.submit")}
        </Button>
      </FieldGroup>
    </form>
  )
}
