package com.kodosi.signin;

import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.services.validation.Validation;

/**
 * The code step for an account whose mail address is not confirmed, as after a sign-up with a provider
 * that does not vouch for the address. It takes the place of Keycloak's Verify Email.
 */
public final class MailCodeAction implements RequiredActionProvider, RequiredActionFactory {
    static final String ID = "kodosi-mail-code";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (!context.getRealm().isVerifyEmail() || user.isEmailVerified() || Validation.isBlank(user.getEmail())) return;
        if (user.getRequiredActionsStream().anyMatch(UserModel.RequiredAction.UPDATE_EMAIL.name()::equals)) return;
        user.addRequiredAction(ID);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user.isEmailVerified()) {
            context.success();
            return;
        }
        if (Validation.isBlank(user.getEmail())) {
            context.ignore();
            return;
        }
        CodeStep step = step(context);
        if (context.getAuthenticationSession().getAuthNote(CodeStep.STARTED) != null) {
            context.challenge(step.show());
            return;
        }
        context.getAuthenticationSession().setAuthNote(CodeStep.STARTED, "true");
        context.challenge(step.start());
    }

    @Override
    public void processAction(RequiredActionContext context) {
        Response page = step(context).post(context.getHttpRequest().getDecodedFormParameters());
        if (page == null) context.success();
        else context.challenge(page);
    }

    private static CodeStep step(RequiredActionContext context) {
        return new CodeStep(context.getSession(), context.getAuthenticationSession(), context.getEvent(),
                context::form, CodeStep.Kind.ACCOUNT, context.getUser());
    }

    @Override public String getId() { return ID; }
    @Override public String getDisplayText() { return "Kodosi mail code"; }
    @Override public RequiredActionProvider create(KeycloakSession session) { return this; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
