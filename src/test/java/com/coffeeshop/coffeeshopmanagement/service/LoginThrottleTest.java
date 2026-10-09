package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoginThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) throttle.recordFailure(user);
    }

    @Test
    public void fourFailuresStillAllowAnAttempt() {
        fail("admin", 4);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void theFifthFailureBlocksForThirtySecondsThenItClears() {
        fail("admin", 5);
        assertEquals(30, throttle.secondsUntilAllowed("admin"));
        now.addAndGet(10_000);
        assertEquals(20, throttle.secondsUntilAllowed("admin"));
        now.addAndGet(20_000);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void eachRepeatedLockoutIsLongerButNeverBeyondFiveMinutes() {
        long previous = 0;
        for (int round = 0; round < 8; round++) {
            fail("admin", 5);
            long wait = throttle.secondsUntilAllowed("admin");
            assertTrue(wait >= previous);
            assertTrue(wait <= 300);
            previous = wait;
            now.addAndGet(wait * 1000);
        }
        assertEquals(300, previous);
    }

    @Test
    public void aSuccessfulLoginResetsTheCounters() {
        fail("admin", 4);
        throttle.recordSuccess("admin");
        fail("admin", 4);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void usernamesAreIndependentAndCaseInsensitive() {
        fail("Admin", 5);
        assertEquals(30, throttle.secondsUntilAllowed(" admin "));
        assertEquals(0, throttle.secondsUntilAllowed("someone-else"));
    }
}
