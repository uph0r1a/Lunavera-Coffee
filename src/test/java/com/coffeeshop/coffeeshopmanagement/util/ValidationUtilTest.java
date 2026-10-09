package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ValidationUtilTest {

    @Test
    public void isBlank() {
        assertTrue(ValidationUtil.isBlank(null));
        assertTrue(ValidationUtil.isBlank(""));
        assertTrue(ValidationUtil.isBlank("   "));
        assertFalse(ValidationUtil.isBlank("a"));
    }

    @Test
    public void validPhoneNumbers() {
        assertTrue(ValidationUtil.isValidPhone("0912345678"));
        assertTrue(ValidationUtil.isValidPhone("+84912345678"));
        assertTrue(ValidationUtil.isValidPhone("84912345678"));
    }

    @Test
    public void invalidPhoneNumbers() {
        assertFalse(ValidationUtil.isValidPhone(null));
        assertFalse(ValidationUtil.isValidPhone(""));
        assertFalse(ValidationUtil.isValidPhone("12345")); // too short
        assertFalse(ValidationUtil.isValidPhone("091234567890123")); // too long
        assertFalse(ValidationUtil.isValidPhone("091-234-5678")); // punctuation not allowed
        assertFalse(ValidationUtil.isValidPhone("abcdefghij"));
    }

    @Test
    public void validEmails() {
        assertTrue(ValidationUtil.isValidEmail("a@b.com"));
        assertTrue(ValidationUtil.isValidEmail("first.last+tag@example.co.vn"));
    }

    @Test
    public void invalidEmails() {
        assertFalse(ValidationUtil.isValidEmail(null));
        assertFalse(ValidationUtil.isValidEmail("not-an-email"));
        assertFalse(ValidationUtil.isValidEmail("missing-at.com"));
        assertFalse(ValidationUtil.isValidEmail("@missing-local.com"));
        assertFalse(ValidationUtil.isValidEmail("no-domain@"));
    }

    @Test
    public void validUsernames() {
        assertTrue(ValidationUtil.isValidUsername("employee_1"));
        assertTrue(ValidationUtil.isValidUsername("abcd")); // exactly the 4-char minimum
    }

    @Test
    public void invalidUsernames() {
        assertFalse(ValidationUtil.isValidUsername(null));
        assertFalse(ValidationUtil.isValidUsername("abc")); // one under the minimum
        assertFalse(ValidationUtil.isValidUsername("a".repeat(33))); // one over the maximum
        assertFalse(ValidationUtil.isValidUsername("bad name")); // space not allowed
        assertFalse(ValidationUtil.isValidUsername("bad@name"));
    }

    @Test
    public void validPasswords() {
        assertTrue(ValidationUtil.isValidPassword("abc123"));
        assertTrue(ValidationUtil.isValidPassword("Correct1"));
    }

    @Test
    public void invalidPasswords() {
        assertFalse(ValidationUtil.isValidPassword(null));
        assertFalse(ValidationUtil.isValidPassword("abc12")); // one under the length minimum
        assertFalse(ValidationUtil.isValidPassword("abcdef")); // no digit
        assertFalse(ValidationUtil.isValidPassword("123456")); // no letter
    }

    @Test
    public void normalizePhoneGivesOneFormForEveryWayOfWritingTheSameNumber() {
        assertEquals("0901111222", ValidationUtil.normalizePhone("0901111222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("+84901111222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("84901111222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("0901 111 222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone("090-1111.222"));
        assertEquals("0901111222", ValidationUtil.normalizePhone(" (0901) 111222 "));
    }

    @Test
    public void normalizePhoneLeavesNonPhonesAloneAndHandlesNull() {
        assertNull(ValidationUtil.normalizePhone(null));
        assertEquals("abc", ValidationUtil.normalizePhone(" abc "));
        assertEquals("", ValidationUtil.normalizePhone(""));
    }
}
