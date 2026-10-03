// Dev-only check (not part of the Maven build, lives outside src/). Confirms AppLogging
// actually writes to a file, not just that install() runs without throwing. Compile/run the
// same way as SessionGuardCheck.java (see progress.md).
import com.coffeeshop.coffeeshopmanagement.util.AppLogging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class AppLoggingCheck {
    public static void main(String[] args) throws Exception {
        AppLogging.install();
        Logger.getLogger("test").info("hello from AppLoggingCheck");
        AppLogging.install(); // second call must be a safe no-op, not a second handler/file

        Path logDir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "logs");
        boolean exists;
        boolean hasMessage = false;
        try (Stream<Path> files = Files.exists(logDir) ? Files.list(logDir) : Stream.empty()) {
            var list = files.toList();
            exists = !list.isEmpty();
            for (Path p : list) {
                if (Files.readString(p).contains("hello from AppLoggingCheck")) hasMessage = true;
            }
        }
        System.out.println((exists ? "PASS" : "FAIL") + " log file created");
        System.out.println((hasMessage ? "PASS" : "FAIL") + " log message actually written to file");
        if (!exists || !hasMessage) System.exit(1);
        System.out.println("ALL PASSED");
    }
}
