package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordUtilTest {

    @Test
    public void correctPasswordMatchesItsOwnHash() {
        String hash = PasswordUtil.hash("Correct1");
        assertTrue(PasswordUtil.matches("Correct1", hash));
    }

    @Test
    public void wrongPasswordDoesNotMatch() {
        String hash = PasswordUtil.hash("Correct1");
        assertFalse(PasswordUtil.matches("Wrong1234", hash));
    }

    @Test
    public void hashIsNeverThePlainPassword() {
        // The whole point of hashing: what's stored must not just be the password itself.
        String hash = PasswordUtil.hash("Correct1");
        assertNotEquals("Correct1", hash);
        assertFalse(hash.contains("Correct1"));
    }

    @Test
    public void sameInputProducesADifferentHashEachTime() {
        // A random salt per call - two users with the same password must not have the same
        // stored hash (that would leak "these two accounts share a password").
        assertNotEquals(PasswordUtil.hash("Correct1"), PasswordUtil.hash("Correct1"));
    }

    @Test
    public void malformedStoredHashFailsClosedRatherThanThrowing() {
        // A corrupted/truncated DB value must be treated as "doesn't match", never as an
        // exception that could be mishandled into an accidental login.
        assertFalse(PasswordUtil.matches("Correct1", "not-a-real-hash"));
        assertFalse(PasswordUtil.matches("Correct1", ""));
        assertFalse(PasswordUtil.matches("Correct1", null));
        assertFalse(PasswordUtil.matches(null, PasswordUtil.hash("Correct1")));
    }
}
