package com.coffeeshop.coffeeshopmanagement.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} wrong attempts in a row for a
 * username, further attempts for it are refused for 30 seconds, then 60, 120... up to 5 minutes.
 * A correct login resets it. It is a delay, never a permanent lock, so nobody (the only admin
 * included) can be locked out for good. State lives in memory for the running app.
 *
 * <p>Usernames that do not exist are throttled exactly like real ones, so the throttle itself does
 * not reveal which usernames exist.
 */
public final class LoginThrottle {

    static final int MAX_FAILURES = 5;
    private static final long BASE_LOCK_MILLIS = 30_000;
    private static final long MAX_LOCK_MILLIS = 5 * 60_000;
    private static final int MAX_TRACKED = 1000;

    private static final class State {
        int failures;
        int lockouts;
        long blockedUntil;
    }

    private final LongSupplier clock;
    private final Map<String, State> states = new HashMap<>();

    public LoginThrottle() {
        this(System::currentTimeMillis);
    }

    public LoginThrottle(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Seconds the caller must still wait for this username, or 0 when an attempt is allowed. */
    public synchronized long secondsUntilAllowed(String username) {
        State state = states.get(key(username));
        if (state == null) return 0;
        long remaining = state.blockedUntil - clock.getAsLong();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public synchronized void recordFailure(String username) {
        if (states.size() >= MAX_TRACKED) {
            states.clear(); // bounded memory; an attacker flooding names only resets everyone's counters
        }
        State state = states.computeIfAbsent(key(username), k -> new State());
        state.failures++;
        if (state.failures >= MAX_FAILURES) {
            long lock = Math.min(MAX_LOCK_MILLIS, BASE_LOCK_MILLIS << Math.min(state.lockouts, 4));
            state.blockedUntil = clock.getAsLong() + lock;
            state.lockouts++;
            state.failures = 0;
        }
    }

    public synchronized void recordSuccess(String username) {
        states.remove(key(username));
    }
}
