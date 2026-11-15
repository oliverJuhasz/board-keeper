import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef, useState } from "react"
import { fetchClient, sessionKey, type Session } from "@/api/client"
import { useSession } from "./useSession"

// Passkeys in the browser. The server sends WebAuthn's options as JSON, the browser turns them
// into what navigator.credentials needs (parseCreationOptionsFromJSON and
// parseRequestOptionsFromJSON), asks the phone, computer or security key, and turns its answer
// back into JSON (toJSON) for the server to check.

/** Whether this browser can make and use passkeys in the JSON form the API speaks. */
export function browserHasPasskeys() {
  return (
    typeof window.PublicKeyCredential === "function" &&
    typeof PublicKeyCredential.parseRequestOptionsFromJSON === "function"
  )
}

/**
 * Whether the browser gave up because the person cancelled, the time ran out, or the page
 * stopped asking. None of these is worth an error message.
 */
export function wasCancelled(error: unknown) {
  return (
    error instanceof DOMException &&
    (error.name === "NotAllowedError" || error.name === "AbortError")
  )
}

/** Makes a new passkey for the options from POST /api/account/passkeys/options. */
export async function makePasskey(options: unknown) {
  const publicKey = PublicKeyCredential.parseCreationOptionsFromJSON(
    options as PublicKeyCredentialCreationOptionsJSON,
  )
  const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential
  return credential.toJSON()
}

/**
 * Asks for one of this site's passkeys, for the options from POST /api/session/passkey/options.
 *
 * @param conditional offer the passkeys among the username field's suggestions, instead of in
 *     a window of their own. The promise then waits until one is picked, or the signal aborts.
 */
async function choosePasskey(options: unknown, conditional: boolean, signal?: AbortSignal) {
  const publicKey = PublicKeyCredential.parseRequestOptionsFromJSON(
    options as PublicKeyCredentialRequestOptionsJSON,
  )
  const credential = (await navigator.credentials.get({
    publicKey,
    mediation: conditional ? "conditional" : "optional",
    signal,
  })) as PublicKeyCredential
  return credential.toJSON()
}

/**
 * Asks for one of the signed-in Account's own passkeys, to confirm that it's really them before
 * something that needs more than being signed in, such as creating an API Key. The options come
 * from the server and name that Account's passkeys.
 */
export function confirmWithPasskey(options: unknown) {
  return choosePasskey(options, false)
}

async function signInWithPasskey(
  staySignedIn: boolean,
  conditional: boolean,
  signal?: AbortSignal,
): Promise<Session> {
  // The signal stops the request for options too. Its answer brings a session of its own, so it
  // must not arrive after a password sign-in has replaced that session with a signed-in one.
  const options = await fetchClient.POST("/api/session/passkey/options", { signal })
  if (options.error) {
    throw options.error
  }
  const credential = await choosePasskey(options.data, conditional, signal)
  const signedIn = await fetchClient.POST("/api/session/passkey", {
    body: { credential, staySignedIn },
  })
  if (signedIn.error) {
    throw signedIn.error
  }
  return signedIn.data
}

/** What a failure that isn't the server's looks like to the page: the server's refusal. */
const notAccepted = { code: "session.passkeyNotAccepted" }

/**
 * Signing in with a passkey, for the sign-in page. Where the browser can, it also offers the
 * passkeys among the username field's suggestions, which needs autocomplete="username webauthn"
 * on that field.
 *
 * @param staySignedIn reads the "Stay signed in" box at the moment of signing in
 */
export function usePasskeySignIn(staySignedIn: () => boolean) {
  const queryClient = useQueryClient()
  const offered = useSession().data?.passkeys === true && browserHasPasskeys()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>()
  // Asking again starts a new round of suggestions, after the button's window was closed.
  const [round, setRound] = useState(0)
  // How to stop the suggestions. A ref keeps it without drawing the page again when it changes.
  const suggestions = useRef<AbortController>(undefined)

  useEffect(() => {
    if (!offered) {
      return
    }
    const controller = new AbortController()
    suggestions.current = controller
    void (async () => {
      if (!(await PublicKeyCredential.isConditionalMediationAvailable())) {
        return
      }
      try {
        // Setting "who is signed in" makes the page move on.
        queryClient.setQueryData(
          sessionKey,
          await signInWithPasskey(staySignedIn(), true, controller.signal),
        )
      } catch (failed) {
        // The browser's own failures here mean it can't offer passkeys among the suggestions,
        // which needs no message: nobody tried one, and the button still works. The server's
        // refusal of a passkey that was picked does.
        if (!controller.signal.aborted && !(failed instanceof DOMException)) {
          setError(failed)
        }
      }
    })()
    // Leaving the page, or a new round, stops the browser from offering them.
    return () => controller.abort()
  }, [offered, round, queryClient, staySignedIn])

  /**
   * Stops offering passkeys among the suggestions, for a sign-in with the password. It has to
   * come before that sign-in is sent: the request for passkey options starts a session of its
   * own, and an answer that arrived afterwards would replace the signed-in session with it.
   */
  function stopSuggestions() {
    suggestions.current?.abort()
  }

  /** Signing in with the button: the browser asks in a window of its own. */
  async function signIn() {
    // A browser waits for one passkey request at a time.
    suggestions.current?.abort()
    setError(undefined)
    setPending(true)
    try {
      queryClient.setQueryData(sessionKey, await signInWithPasskey(staySignedIn(), false))
    } catch (failed) {
      if (!wasCancelled(failed)) {
        setError(failed instanceof DOMException ? notAccepted : failed)
      }
      setRound((current) => current + 1)
    } finally {
      setPending(false)
    }
  }

  return { offered, signIn, stopSuggestions, pending, error }
}
