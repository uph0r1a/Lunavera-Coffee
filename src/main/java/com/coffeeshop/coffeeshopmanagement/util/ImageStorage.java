package com.coffeeshop.coffeeshopmanagement.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Owns where product images live on disk and what gets stored in {@code products.image_path}.
 *
 * TODO.md item 10a originally stored the *full absolute path* in the database (e.g.
 * {@code /home/alice/.lunavera-coffee/images/172..._latte.png}), which breaks the moment the
 * database is copied to another machine or another user account - exactly the kind of thing
 * {@link com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig} otherwise takes care to
 * avoid. New saves now store just the file name; {@link #resolve} still understands an old
 * absolute path already sitting in an existing database, so upgrading doesn't orphan anyone's
 * pictures.
 */
public final class ImageStorage {

    private ImageStorage() {
    }

    private static Path directoryPath() {
        return Path.of(System.getProperty("user.home"), ".lunavera-coffee", "images");
    }

    public static Path directory() throws IOException {
        Path dir = directoryPath();
        Files.createDirectories(dir);
        return dir;
    }

    /** Copies {@code source} into the managed images directory under a collision-proof name
     *  and returns the value to store on the product ({@code Product.setImagePath(...)}) - a
     *  bare file name, not a path. */
    public static String storeNewFile(File source) throws IOException {
        Path dir = directory();
        String fileName = System.currentTimeMillis() + "_" + source.getName();
        Files.copy(source.toPath(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    /** Turns a stored value (new-style bare file name, or an old-style absolute path from
     *  before this fix) into the real file, or null if it can't be resolved / doesn't exist.
     *  Read-only: unlike {@link #directory()}, never creates the images directory. */
    public static File resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        File direct = new File(stored);
        // An old absolute/relative path (contains a separator, or is itself already absolute)
        // is used as-is; a bare file name is resolved against the managed directory.
        File candidate = (stored.indexOf('/') >= 0 || stored.indexOf('\\') >= 0 || direct.isAbsolute())
                ? direct
                : directoryPath().resolve(stored).toFile();
        return candidate.exists() ? candidate : null;
    }

    /** Best-effort delete - never throws. A missing file, an old value that never resolves, or
     *  a locked file on the OS shouldn't block saving/deleting the product record itself. */
    public static void deleteQuietly(String stored) {
        File file = resolve(stored);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException | RuntimeException ignored) {
            // Not worth surfacing to the user.
        }
    }
}
