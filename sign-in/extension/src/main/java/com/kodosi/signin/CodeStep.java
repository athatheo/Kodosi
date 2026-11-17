package com.kodosi.signin;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import org.keycloak.common.util.ObjectUtil;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.light.LightweightUserAdapter;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;

/** The pages, the mails and the form posts of the code step, in each place that asks for a code. */
final class CodeStep {
    enum Kind {
        /** A sign-up: the code comes before the account. */
        SIGN_UP("sign-up"),
        /** A forgotten password: the code goes to the address of the account, if there is one. */
        RESET("reset"),
        /** A signed-in account whose address is not confirmed. */
        ACCOUNT("account");

        final String word;

        Kind(String word) {
            this.word = word;
        }
    }

    static final String CONFIRMED = "kodosi.code.confirmed";
    static final String STARTED = "kodosi.code.started";
    private static final String ADDRESS = "kodosi.code.address";
    private static final String EDITING = "kodosi.code.editing";
    private static final int LONGEST_ADDRESS = 254;
    private static final Logger LOG = Logger.getLogger(CodeStep.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AuthenticationSessionModel authSession;
    private final EventBuilder event;
    private final Supplier<LoginFormsProvider> forms;
    private final Kind kind;
    private final UserModel account;
    private final MailCode codes;

    /**
     * {@code account} is the signed-in account for {@link Kind#ACCOUNT}, the account that a reset is for (or
     * null when none matched) for {@link Kind#RESET}, and null for {@link Kind#SIGN_UP}.
     */
    CodeStep(KeycloakSession session, AuthenticationSessionModel authSession, EventBuilder event,
            Supplier<LoginFormsProvider> forms, Kind kind, UserModel account) {
        this.session = session;
        this.realm = authSession.getRealm();
        this.authSession = authSession;
        this.event = event;
        this.forms = forms;
        this.kind = kind;
        this.account = account;
        SingleUseObjectProvider store = session.singleUseObjects();
        this.codes = new MailCode(new MailCode.Notes() {
            @Override public String get(String name) { return authSession.getAuthNote(name); }
            @Override public void set(String name, String value) { authSession.setAuthNote(name, value); }
            @Override public void remove(String name) { authSession.removeAuthNote(name); }
        }, new MailCode.Store() {
            @Override public boolean hold(String key, int seconds) { return store.putIfAbsent(key, seconds); }
            @Override public void drop(String key) { store.remove(key); }
        }, Time.currentTimeSeconds(), realm.getActionTokenGeneratedByUserLifespan());
    }

    Response start() {
        if (kind == Kind.SIGN_UP) return show();
        if (kind == Kind.ACCOUNT) authSession.setAuthNote(ADDRESS, normal(account.getEmail()));
        return send(false);
    }

    Response show() {
        if (kind != Kind.RESET && (address() == null || authSession.getAuthNote(EDITING) != null)) {
            return emailPage(address(), null);
        }
        return codePage(false, null, null);
    }

    /** Answers a form post: the next page, or null when the code passed. */
    Response post(MultivaluedMap<String, String> form) {
        if (form.containsKey("resend")) return send(true);
        if (kind != Kind.RESET) {
            if (form.containsKey("other")) {
                authSession.setAuthNote(EDITING, "true");
                return emailPage(address(), null);
            }
            if (form.containsKey("back")) {
                authSession.removeAuthNote(EDITING);
                return show();
            }
            if (form.containsKey("email")) return choose(form.getFirst("email"));
        }
        if (!form.containsKey("code")) return show();
        String address = recipient();
        return switch (codes.check(form.getFirst("code"), address)) {
            case PASSED -> confirm(address) ? null : refuse("kdsCodeWrong", Errors.EMAIL_IN_USE);
            case WRONG -> refuse("kdsCodeWrong", Errors.INVALID_CODE);
            case ENDED -> refuse("kdsCodeEnded", Errors.INVALID_CODE);
            case OLD -> refuse("kdsCodeOld", Errors.EXPIRED_CODE);
        };
    }

    private Response choose(String typed) {
        String address = normal(typed);
        if (address == null || address.length() > LONGEST_ADDRESS || !Validation.isEmailValid(address)) {
            return emailPage(typed, "kdsBadEmail");
        }
        if (codes.tooOften()) return emailPage(typed, "kdsCodeLater");
        authSession.removeAuthNote(EDITING);
        if (!address.equals(address())) {
            authSession.setAuthNote(ADDRESS, address);
            codes.forget();
        }
        if (codes.waitSeconds() > 0) return codePage(false, null, null);
        return send(false);
    }

    private Response send(boolean again) {
        String to = recipient();
        UserModel owner = to == null || kind == Kind.RESET || realm.isDuplicateEmailsAllowed()
                ? null
                : session.users().getUserByEmail(realm, to);
        boolean codeWorks = owner == null || (kind == Kind.ACCOUNT && owner.getId().equals(account.getId()));
        MailCode.Send sent = codes.send(to, codeWorks);
        boolean mailed = switch (sent.mail()) {
            case NONE -> true;
            case CODE -> mail(to, "kdsCodeSubject", List.of(sent.code(), realmName()), "kodosi-code.ftl",
                    Map.of("code", sent.code(), "minutes", Math.max(1, realm.getActionTokenGeneratedByUserLifespan() / 60)));
            case ACCOUNT -> mail(to, "kdsAccountSubject", List.of(realmName()), "kodosi-account.ftl",
                    Map.of("username", owner.getUsername()));
        };
        // A reset page must look the same whether a mail went out or not.
        boolean shownAsSent = mailed || kind == Kind.RESET;
        String problem = sent.shown() == MailCode.Shown.LATER ? "kdsCodeLater" : shownAsSent ? null : "kdsCodeUnsent";
        return codePage(again && sent.shown() == MailCode.Shown.SENT && shownAsSent, null, problem);
    }

    /** Takes the address whose code passed; false when another account took it after its code went out. */
    private boolean confirm(String address) {
        if (kind == Kind.ACCOUNT && !address.equals(normal(account.getEmail()))) {
            UserModel other = session.users().getUserByEmail(realm, address);
            if (other != null && !other.getId().equals(account.getId())) return false;
            account.setEmail(address);
        }
        switch (kind) {
            case SIGN_UP -> authSession.setAuthNote(CONFIRMED, address);
            case RESET -> {
                account.setEmailVerified(true);
                account.removeRequiredAction(MailCodeAction.ID);
            }
            case ACCOUNT -> account.setEmailVerified(true);
        }
        event.clone().event(EventType.VERIFY_EMAIL).detail(Details.EMAIL, address).success();
        return true;
    }

    private Response refuse(String words, String error) {
        event.clone().detail(Details.EMAIL, recipient()).error(error);
        return codePage(false, words, null);
    }

    /** The address that codes go to, or null when there is none, as for a reset of no account. */
    private String recipient() {
        return switch (kind) {
            case RESET -> account == null ? null : normal(account.getEmail());
            case SIGN_UP, ACCOUNT -> address();
        };
    }

    private String address() {
        return authSession.getAuthNote(ADDRESS);
    }

    private Response emailPage(String address, String problem) {
        LoginFormsProvider form = forms.get()
                .setAttribute("kodosiFlow", kind.word)
                .setAttribute("kodosiEmail", address)
                .setAttribute("kodosiBack", address() != null);
        if (problem != null) form.addError(new FormMessage("email", problem));
        return form.createForm("kodosi-email.ftl");
    }

    private Response codePage(boolean resent, String wrong, String problem) {
        LoginFormsProvider form = forms.get()
                .setAttribute("kodosiFlow", kind.word)
                .setAttribute("kodosiDigits", MailCode.DIGITS)
                .setAttribute("kodosiWait", codes.waitSeconds())
                .setAttribute("kodosiResent", resent);
        if (kind != Kind.RESET) form.setAttribute("kodosiEmail", address());
        if (wrong != null) form.addError(new FormMessage("code", wrong));
        if (problem != null) form.setError(problem);
        return form.createForm("kodosi-code.ftl");
    }

    // Keycloak's mail templates need a user, and a sign-up has none yet. This one carries the address and
    // the language of the page that asked for the mail.
    private boolean mail(String to, String subject, List<Object> subjectValues, String template, Map<String, Object> values) {
        UserModel reader = new LightweightUserAdapter(session, realm, null);
        reader.setEmail(to);
        Locale locale = session.getContext().resolveLocale(kind == Kind.ACCOUNT ? account : null);
        reader.setSingleAttribute(UserModel.LOCALE, locale.toLanguageTag());
        EventBuilder sending = event.clone()
                .event(kind == Kind.RESET ? EventType.SEND_RESET_PASSWORD : EventType.SEND_VERIFY_EMAIL)
                .detail(Details.EMAIL, to);
        try {
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setAuthenticationSession(authSession)
                    .setUser(reader)
                    .send(subject, subjectValues, template, new HashMap<>(values), to);
            sending.success();
            return true;
        } catch (EmailException e) {
            LOG.warn("The mail of the code step did not go out", e);
            sending.detail(Details.REASON, e.getMessage()).error(Errors.EMAIL_SEND_FAILED);
            return false;
        }
    }

    private String realmName() {
        return realm.getDisplayName() == null ? ObjectUtil.capitalize(realm.getName()) : realm.getDisplayName();
    }

    private static String normal(String address) {
        return address == null || address.isBlank() ? null : address.trim().toLowerCase(Locale.ROOT);
    }
}
