import type { ExtendKcContext } from "keycloakify/login";
import type { KcEnvName, ThemeName } from "../kc.gen";

type KcContextExtension = {
  themeName: ThemeName;
  properties: Record<KcEnvName, string>;
};

type KcContextExtensionPerPage = {
  "turnstile-form.ftl": {
    turnstileSiteKey: string;
    isResetFlow?: boolean;
    isRegistrationFlow?: boolean;
  };
  "turnstile-registration-form.ftl": { turnstileSiteKey: string };
  "login-idp-link-confirm-override.ftl": { brokerContext?: { username: string } };
  "register.ftl": { kodosiEmail?: string };
  "kodosi-email.ftl": { kodosiFlow: CodeFlow; kodosiEmail?: string; kodosiBack: boolean };
  "kodosi-code.ftl": {
    kodosiFlow: CodeFlow;
    kodosiEmail?: string;
    kodosiDigits: number;
    kodosiWait: number;
    kodosiResent: boolean;
  };
};

// A reset page shows no address: it would tell that the name has an account.
type CodeFlow = "sign-up" | "reset" | "account";

export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>;
