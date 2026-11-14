import { Check, Mail, SquareArrowOutUpRight } from "lucide-react";
import { useEffect, useState } from "react";

import { CodeCells } from "../../parts/code";
import { ActionRow, Go, TextRow } from "../../parts/rows";
import { QuietLink, quietLink } from "../../parts/text";
import { IconTile } from "../../parts/tile";
import { Stage } from "../stage";
import type { Page } from "./props";

const INBOXES = [
  { name: "Gmail", href: "https://mail.google.com/", ends: ["gmail.com", "googlemail.com"] },
  {
    name: "Outlook",
    href: "https://outlook.live.com/mail/",
    ends: ["outlook.com", "hotmail.com", "live.com", "msn.com"],
  },
  { name: "iCloud Mail", href: "https://www.icloud.com/mail", ends: ["icloud.com", "me.com"] },
  {
    name: "Proton Mail",
    href: "https://mail.proton.me/",
    ends: ["proton.me", "protonmail.com", "pm.me"],
  },
  { name: "Yahoo Mail", href: "https://mail.yahoo.com/", ends: ["yahoo.com", "ymail.com"] },
  { name: "Fastmail", href: "https://app.fastmail.com/", ends: ["fastmail.com", "fastmail.fm"] },
];

function inboxOf(address: string | undefined) {
  const domain = address?.split("@")[1]?.trim().toLowerCase();
  return domain ? INBOXES.find((one) => one.ends.includes(domain)) : undefined;
}

const SENT_SHOWN_MS = 2600;

export function Email({ kcContext, i18n }: Page<"kodosi-email.ftl">) {
  const { url, messagesPerField, kodosiFlow, kodosiEmail, kodosiBack } = kcContext;
  const { msgStr } = i18n;
  const wrong = messagesPerField.existsError("email");
  const form = "kodosi-email-form";
  return (
    <Stage
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("kdsEmailTitle")}
      lead={msgStr("kdsEmailLead")}
      wrong={wrong}
      problem={wrong ? messagesPerField.get("email") : undefined}
      quiet={wrong}
      below={
        <>
          {kodosiBack && (
            <button type="submit" form={form} name="back" value="true" className={quietLink}>
              {msgStr("kdsBack")}
            </button>
          )}
          {kodosiFlow === "sign-up" && (
            <QuietLink href={url.loginUrl}>{msgStr("kdsHaveAccount")}</QuietLink>
          )}
        </>
      }
    >
      <form id={form} action={url.loginAction} method="post" noValidate>
        <TextRow
          id="email"
          name="email"
          type="email"
          label={msgStr("kdsEmail")}
          defaultValue={kodosiEmail ?? ""}
          autoFocus
          autoComplete="email"
          required
          aria-invalid={wrong}
          end={<Go label={msgStr("kdsSendCode")} />}
        />
      </form>
    </Stage>
  );
}

export function MailCode({ kcContext, i18n }: Page<"kodosi-code.ftl">) {
  const { url, messagesPerField, auth, kodosiFlow, kodosiEmail, kodosiDigits } = kcContext;
  const { msgStr } = i18n;
  const reset = kodosiFlow === "reset";
  const named = auth?.attemptedUsername ?? "";
  const wrong = messagesPerField.existsError("code");
  const form = "kodosi-code-form";
  const inbox = inboxOf(reset ? named : kodosiEmail);
  const [wait, setWait] = useState(kcContext.kodosiWait);
  const [sent, setSent] = useState(kcContext.kodosiResent);
  useEffect(() => {
    if (wait <= 0) return;
    const timer = window.setTimeout(() => setWait((now) => now - 1), 1000);
    return () => clearTimeout(timer);
  }, [wait]);
  useEffect(() => {
    if (!sent) return;
    const timer = window.setTimeout(() => setSent(false), SENT_SHOWN_MS);
    return () => clearTimeout(timer);
  }, [sent]);

  const later = <span className="tabular-nums">{msgStr("kdsSendAgainIn", `${wait}`)}</span>;
  const resend = sent ? (
    <ActionRow
      type="button"
      disabled
      tile={<IconTile tint="green" icon={<Check />} />}
      title={<span className="pop inline-block">{msgStr("kdsSentAgain")}</span>}
      detail={wait > 0 && later}
    />
  ) : wait > 0 ? (
    <ActionRow
      type="button"
      disabled
      tile={<IconTile tint="teal" icon={<Mail />} />}
      title={msgStr("kdsSendNewCode")}
      detail={later}
    />
  ) : (
    <ActionRow
      name="resend"
      value="true"
      tile={<IconTile tint="teal" icon={<Mail />} />}
      title={msgStr("kdsSendNewCode")}
    />
  );

  return (
    <Stage
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("kdsMailCodeTitle")}
      lead={
        reset ? msgStr("kdsMailCodeLeadReset", named) : msgStr("kdsMailCodeLead", kodosiEmail ?? "")
      }
      wrong={wrong}
      problem={wrong ? messagesPerField.get("code") : undefined}
      quiet={wrong}
      below={
        reset ? (
          <QuietLink href={url.loginUrl}>{msgStr("kdsBackToSignIn")}</QuietLink>
        ) : (
          <button type="submit" form={form} name="other" value="true" className={quietLink}>
            {msgStr("kdsOtherEmail")}
          </button>
        )
      }
    >
      <form id={form} action={url.loginAction} method="post" noValidate>
        <CodeCells
          id="code"
          name="code"
          length={kodosiDigits}
          half
          label={msgStr("kdsMailCode")}
          invalid={wrong}
          autoFocus
        />
        {inbox && (
          <ActionRow
            href={inbox.href}
            target="_blank"
            rel="noreferrer"
            tile={<IconTile tint="teal" icon={<Mail />} waits />}
            title={msgStr("kdsOpenMail", inbox.name)}
            end={<SquareArrowOutUpRight className="size-4 shrink-0 text-ink-faint" aria-hidden />}
          />
        )}
        {resend}
      </form>
    </Stage>
  );
}
