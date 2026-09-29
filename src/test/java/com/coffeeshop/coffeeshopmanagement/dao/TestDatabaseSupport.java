package com.coffeeshop.coffeeshopmanagement.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All DAO tests share one throwaway SQLite database for the whole test JVM run.
 * {@code DatabaseConfig.DB_URL} is a {@code static final} field resolved the first time that
 * class is loaded, from the {@code user.home} system property at that moment - so
 * {@code user.home} must point at a fresh temp directory *before* anything touches
 * {@code DatabaseConfig}, and every DAO test class must go through this one shared bootstrap
 * rather than each pointing at its own directory (only whichever test class happened to run
 * first would actually take effect; the rest would silently share its database instead of
 * getting their own). Reading {@link #TEMP_HOME} from a test's {@code @BeforeClass} forces this
 * static initializer to run before that test ever calls a real {@code DatabaseConfig} method.
 *
 * Because every DAO test shares this one database for the run, tests here don't rely on the
 * table being empty (no assertions like "findAll().size() == 1") - each test creates its own
 * uniquely-named row(s) (a random suffix) and only asserts about the row it created. The whole
 * temp directory is discarded when the process ends, so there is nothing to clean up afterward
 * and no delete-after-test bookkeeping needed even for DAOs (like CustomerDAO) with no delete
 * method at all.
 */
final class TestDatabaseSupport {

    static final Path TEMP_HOME;

    static {
        try {
            TEMP_HOME = Files.createTempDirectory("lunavera-dao-test-home");
            System.setProperty("user.home", TEMP_HOME.toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestDatabaseSupport() {
    }

    /** Call from @BeforeClass: touches TEMP_HOME (running the static block above if it hasn't
     *  run yet) then initializes the schema - safe to call from every DAO test class, since
     *  DatabaseConfig.initialize() is itself idempotent. */
    static void ensureReady() {
        Object forceStaticInit = TEMP_HOME;
        com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.initialize();
    }
}
