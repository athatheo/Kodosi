package com.kodosi.signin;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.keycloak.Config;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Keycloak's time, for the account page. Keycloak accepts a DPoP proof only within seconds of its own clock,
 * and its responses carry no Date header that a page could read it from.
 */
public final class ServerTime implements RealmResourceProvider, RealmResourceProviderFactory {
    static final String ID = "kodosi-time";

    // Keycloak serves the methods of a realm resource only from a @Provider class, in a jar with
    // META-INF/beans.xml.
    @Provider
    public static final class Now {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Response get() {
            CacheControl never = new CacheControl();
            never.setNoStore(true);
            return Response.ok(Map.of("now", Time.currentTimeMillis())).cacheControl(never).build();
        }
    }

    @Override public Object getResource() { return new Now(); }
    @Override public RealmResourceProvider create(KeycloakSession session) { return this; }
    @Override public String getId() { return ID; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
