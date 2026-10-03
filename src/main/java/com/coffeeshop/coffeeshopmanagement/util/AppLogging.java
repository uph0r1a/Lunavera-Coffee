package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Adds a rotating log file under ~/.lunavera-coffee/logs/ to java.util.logging's root logger,
 * on top of (not instead of) the console output every part of the app already logs to. Call
 * once, as early as possible in startup (before DatabaseConfig.initialize(), so even DB setup
 * problems land in the file) - safe to call more than once, a second call is a no-op.
 *
 * This exists because the only way to debug a problem on a shop's actual machine after the fact
 * was previously "ask them to somehow capture console output", which isn't realistic for a
 * desktop app with no terminal in sight during normal use.
 */
public final class AppLogging {

    private static volatile boolean installed = false;

    private AppLogging() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            Path logDir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "logs");
            Files.createDirectories(logDir);
            // %g = generation index (0,1,2) for rotation; %u disambiguates if two instances of
            // the app somehow ran at once. 1 MB x 3 files is plenty for a single-till desktop
            // app's log and bounded enough to never quietly fill a shop's disk.
            FileHandler fileHandler = new FileHandler(
                    logDir.resolve("app-%u-%g.log").toString(), 1_000_000, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            Logger.getLogger("").addHandler(fileHandler);
            installed = true;
        } catch (IOException e) {
            // Falls back to console-only logging, which is how the app already behaved before
            // this existed - never worth crashing startup over a logging convenience.
            Logger.getLogger(AppLogging.class.getName())
                    .log(Level.WARNING, "Could not set up file logging; continuing with console only", e);
        }
    }
}
