package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

/**
 * TODO.md "dialog ... input length limits": nothing stopped someone pasting an arbitrarily long
 * string into a name/description field before this - not a crash risk (SQLite TEXT has no
 * length limit), but an absurdly long category/product name breaks table-row/receipt layout and
 * is almost certainly a mistake (a pasted paragraph, not a name) rather than intentional input.
 * A {@link TextFormatter} rejects the keystroke/paste outright rather than silently truncating
 * after the fact, so the field never visibly accepts text it's about to cut off.
 * {@code TextInputControl} covers both {@code TextField} and {@code TextArea}.
 */
public final class TextLengthLimiter {

    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    /** Longest phone we accept: +84 and 10 digits is 13; 15 is the E.164 maximum. */
    public static final int PHONE_MAX = 15;
    public static final int EMAIL_MAX = 100;
    public static final int USERNAME_MAX = 32;
    public static final int PASSWORD_MAX = 64;
    public static final int SEARCH_MAX = 100;
    /** Money / quantity inputs: up to 12 characters (digits, plus separators typed by hand). */
    public static final int NUMBER_MAX = 12;

    private TextLengthLimiter() {
    }

    /** Apply before setting an initial value longer than maxLength - the formatter filters
     *  every change including the very first setText(), so installing it after a too-long
     *  initial value would immediately reject that initial set. None of this app's existing
     *  callers do that (editing an existing row's name is always already within these limits),
     *  but worth knowing if that ever changes. */
    public static void limit(TextInputControl field, int maxLength) {
        field.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= maxLength ? change : null));
    }
}
