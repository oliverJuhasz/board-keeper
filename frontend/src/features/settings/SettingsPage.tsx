import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { z } from "zod"
import { api } from "@/api/client"
import { FormProblem } from "@/components/FormProblem"
import { TextField } from "@/components/TextField"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { FieldGroup } from "@/components/ui/field"
import { newPassword, required } from "@/features/session/accountRules"
import { LanguageSelect } from "@/i18n/LanguageSelect"

const passwordForm = z.object({ currentPassword: required, newPassword })

/** What someone can change about their own Account: the language and the password. */
export function SettingsPage() {
  const { t } = useTranslation()
  const form = useForm({
    resolver: zodResolver(passwordForm),
    defaultValues: { currentPassword: "", newPassword: "" },
  })
  const changePassword = api.useMutation("put", "/api/account/password", {
    onSuccess: () => form.reset(),
  })

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-xl font-semibold">{t("settings.title")}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{t("settings.language")}</CardTitle>
          <CardDescription>{t("settings.languageHint")}</CardDescription>
        </CardHeader>
        <CardContent>
          <LanguageSelect />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("settings.password.title")}</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={form.handleSubmit((values) => changePassword.mutate({ body: values }))}
            noValidate
          >
            <FieldGroup>
              <FormProblem error={changePassword.error} />
              {changePassword.isSuccess && (
                <Alert>
                  <AlertDescription>{t("settings.password.changed")}</AlertDescription>
                </Alert>
              )}
              <TextField
                label={t("settings.password.current")}
                type="password"
                autoComplete="current-password"
                error={form.formState.errors.currentPassword}
                {...form.register("currentPassword")}
              />
              <TextField
                label={t("settings.password.new")}
                hint={t("setup.passwordHint")}
                type="password"
                autoComplete="new-password"
                error={form.formState.errors.newPassword}
                {...form.register("newPassword")}
              />
              <Button type="submit" className="self-start" disabled={changePassword.isPending}>
                {t("settings.password.submit")}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
