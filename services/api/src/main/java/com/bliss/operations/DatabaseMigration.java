package com.bliss.operations;

import java.sql.*;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;

/** Standalone entry point: never initializes Quarkus, HTTP, CDI or workflow workers. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        // Flyway errors can contain SQL, connection details or application data. This separate
        // process emits only fixed stages/counts, including when validation or migration fails.
        Log quiet =
                new Log() {
                    public void debug(String message) {}

                    public void info(String message) {}

                    public void warn(String message) {}

                    public void error(String message) {}

                    public void error(String message, Exception cause) {}

                    public void notice(String message) {}
                };
        LogFactory.setLogCreator(type -> quiet);
        LogFactory.setFallbackLogCreator(type -> quiet);
        System.exit(execute(args, System.getenv()));
    }

    private static int execute(String[] args, Map<String, String> env) {
        String stage = "configuration";
        try {
            if (args.length != 3
                    || !(args[0].equals("validate") || args[0].equals("migrate"))
                    || !args[1].equals("--database")
                    || args[2].isBlank()) {
                return refused(stage, 2);
            }
            String url = required(env, "BLISS_MIGRATION_DB_URL");
            String user = required(env, "BLISS_MIGRATION_DB_USER");
            String password = required(env, "BLISS_MIGRATION_DB_PASSWORD");
            if (!url.startsWith("jdbc:postgresql://")) return refused(stage, 2);

            stage = "preflight";
            var properties = new java.util.Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", password);
            properties.setProperty("connectTimeout", "10");
            try (var connection = DriverManager.getConnection(url, properties);
                    var statement =
                            connection.prepareStatement(
                                    """
                            SELECT current_database() = ? AND current_user = ?
                              AND r.rolcanlogin
                              AND NOT (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole
                                       OR r.rolreplication OR r.rolbypassrls)
                              AND NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member=r.oid)
                              AND (SELECT nspowner FROM pg_namespace WHERE nspname='public')=r.oid
                              AND NOT EXISTS (
                                SELECT 1 FROM pg_class WHERE relnamespace='public'::regnamespace
                                  AND relkind IN ('r','p','v','m','S','f') AND relowner<>r.oid)
                            FROM pg_roles r WHERE r.rolname=current_user
                            """)) {
                statement.setQueryTimeout(10);
                statement.setString(1, args[2]);
                statement.setString(2, user);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) return refused(stage, 3);
                }
            }

            stage = "validation";
            var flyway =
                    Flyway.configure()
                            .loggers("console")
                            .dataSource(url, user, password)
                            .jdbcProperties(Map.of("connectTimeout", "10"))
                            .locations("classpath:db/migration")
                            .defaultSchema("public")
                            .schemas("public")
                            .createSchemas(false)
                            .cleanDisabled(true)
                            .baselineOnMigrate(false)
                            .outOfOrder(false)
                            .validateMigrationNaming(true)
                            .validateOnMigrate(true)
                            .ignoreMigrationPatterns(new String[0])
                            .failOnMissingLocations(true)
                            .load();
            int applied = 0;
            if (args[0].equals("migrate")) {
                stage = "migration";
                applied = flyway.migrate().migrationsExecuted;
            }
            stage = "validation";
            flyway.validate();
            if (flyway.info().pending().length != 0) return refused(stage, 4);
            System.out.println(
                    "bliss_migration success=true action=" + args[0] + " applied=" + applied);
            return 0;
        } catch (IllegalArgumentException e) {
            return refused(stage, stage.equals("configuration") ? 2 : 4);
        } catch (Exception e) {
            return refused(stage, stage.equals("preflight") ? 3 : 4);
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    private static int refused(String stage, int code) {
        System.err.println("bliss_migration success=false stage=" + stage);
        return code;
    }
}
