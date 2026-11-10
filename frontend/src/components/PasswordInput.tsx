import { EyeIcon, EyeOffIcon } from "lucide-react"
import { useState, type ComponentProps } from "react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

/** A password input with a button to show what was typed. Long passwords are easy to mistype. */
export function PasswordInput(props: ComponentProps<"input">) {
  const { t } = useTranslation()
  const [shown, setShown] = useState(false)

  return (
    <div className="relative">
      <Input {...props} type={shown ? "text" : "password"} className="pr-9" />
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="absolute top-1/2 right-0.5 -translate-y-1/2"
        aria-label={shown ? t("password.hide") : t("password.show")}
        aria-pressed={shown}
        onClick={() => setShown(!shown)}
      >
        {shown ? <EyeOffIcon /> : <EyeIcon />}
      </Button>
    </div>
  )
}
