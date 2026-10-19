package com.bliss.operations;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.*;

/** Startup-configured REST admission switch, independent of dispatch and engine restoration. */
@Readiness
@ApplicationScoped
public class WriteAdmission implements HealthCheck {
    @ConfigProperty(name = "bliss.operations.writes-paused", defaultValue = "false")
    boolean paused;

    public boolean paused() {
        return paused;
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("API write admission").status(!paused).build();
    }
}
