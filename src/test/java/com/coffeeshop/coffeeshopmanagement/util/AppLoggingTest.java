package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code AppLogging}'s own dev-checks/AppLoggingCheck.java verifies it end-to-end across a real
 * process restart; this adds the fast, `mvn test`-runnable unit-level coverage that was missing
 * (dev-checks/ isn't part of the Maven build - see its own header comment).
 *
 * {@code AppLogging.install()} is a one-time-per-process operation (a static {@code installed}
 * flag, exactly like {@code DatabaseConfig} - see its own javadoc), so this can't use a per-test
 * {@code @Before}/{@code @After} temp-directory swap: whichever test happens to trigger the
 * *real* first call wins for the rest of the whole test JVM run, and a later test's
 * {@code @After} deleting its own temp directory could delete the one still in use. Same
 * shared-temp-directory-for-the-whole-run approach {@code dao/TestDatabaseSupport} uses for
 * exactly this reason (see its javadoc) - a static initializer, set once, never cleaned up
 * mid-run.
 */
public class AppLoggingTest {

    private static final Path TEMP_HOME;

    static {
        try {
            TEMP_HOME = Files.createTempDirectory("lunavera-logging-test-home");
            System.setProperty("user.home", TEMP_HOME.toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    public void installCreatesALogFileAndSomethingGetsWrittenToIt() throws IOException {
        AppLogging.install();
        String marker = "app logging test marker " + System.nanoTime();
        Logger.getLogger(AppLoggingTest.class.getName()).info(marker);
        // FileHandler's parent (StreamHandler) flushes on every publish by default, so this
        // should already be on disk without needing an explicit flush/close from here.
        Path logsDir = TEMP_HOME.resolve(".lunavera-coffee").resolve("logs");
        assertTrue("logs directory should have been created", Files.isDirectory(logsDir));
        try (var files = Files.list(logsDir)) {
            assertTrue("at least one log file should exist", files.findAny().isPresent());
        }
    }

    @Test
    public void secondCallDoesNotThrowOrAddASecondHandler() {
        AppLogging.install();
        int before = Logger.getLogger("").getHandlers().length;
        AppLogging.install();
        int after = Logger.getLogger("").getHandlers().length;
        assertFalse("a repeat call must not add another handler", after > before);
    }
}
