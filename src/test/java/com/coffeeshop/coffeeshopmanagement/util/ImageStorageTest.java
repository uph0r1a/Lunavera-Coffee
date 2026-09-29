package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ImageStorageTest {

    private String realUserHome;
    private Path fakeHome;

    @Before
    public void useAThrowawayHomeDirectory() throws IOException {
        // ImageStorage reads user.home directly (matches DatabaseConfig's own convention), so
        // this points it at a temp directory rather than touching the real
        // ~/.lunavera-coffee/images on whatever machine runs the tests.
        realUserHome = System.getProperty("user.home");
        fakeHome = Files.createTempDirectory("lunavera-image-test");
        System.setProperty("user.home", fakeHome.toString());
    }

    @After
    public void restoreRealHomeDirectory() throws IOException {
        System.setProperty("user.home", realUserHome);
        try (var walk = Files.walk(fakeHome)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void storedFileCanBeResolvedBackToItself() throws IOException {
        File source = Files.createTempFile("source", ".png").toFile();
        Files.writeString(source.toPath(), "fake image bytes");

        String stored = ImageStorage.storeNewFile(source);

        // What gets stored on the product is a bare file name, not a path (TODO.md item 10a) -
        // so a database copied to another machine/user still finds its images.
        assertFalse("stored value must not be an absolute path", new File(stored).isAbsolute());
        File resolved = ImageStorage.resolve(stored);
        assertNotNull(resolved);
        assertTrue(resolved.exists());
        assertEquals("fake image bytes", Files.readString(resolved.toPath()));
    }

    @Test
    public void oldAbsolutePathFromBeforeTheFixStillResolves() throws IOException {
        // Simulates a database written before this session's change, where the full path was
        // stored directly - upgrading must not orphan anyone's already-saved pictures.
        File legacy = Files.createTempFile("legacy", ".png").toFile();
        File resolved = ImageStorage.resolve(legacy.getAbsolutePath());
        assertNotNull(resolved);
        assertEquals(legacy.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void missingFileResolvesToNullRatherThanAFileThatDoesNotExist() {
        assertNull(ImageStorage.resolve("this-file-was-never-saved.png"));
        assertNull(ImageStorage.resolve("/no/such/absolute/path.png"));
    }

    @Test
    public void blankOrNullNeverResolves() {
        assertNull(ImageStorage.resolve(null));
        assertNull(ImageStorage.resolve(""));
        assertNull(ImageStorage.resolve("   "));
    }

    @Test
    public void deleteQuietlyActuallyDeletesAResolvableFile() throws IOException {
        File source = Files.createTempFile("todelete", ".png").toFile();
        String stored = ImageStorage.storeNewFile(source);
        File saved = ImageStorage.resolve(stored);
        assertTrue(saved.exists());

        ImageStorage.deleteQuietly(stored);

        assertNull(ImageStorage.resolve(stored));
    }

    @Test
    public void deleteQuietlyNeverThrowsOnUnresolvableInput() {
        // Must be safe to call unconditionally after a product delete, even if the product
        // never had a picture or its file is already gone.
        ImageStorage.deleteQuietly(null);
        ImageStorage.deleteQuietly("");
        ImageStorage.deleteQuietly("never-existed.png");
    }
}
