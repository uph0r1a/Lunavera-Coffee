package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PagerTest {

    private List<Integer> numbers(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).boxed().toList();
    }

    @Test
    public void splitsItemsAcrossPages() {
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(numbers(25));
        assertEquals(3, pager.getTotalPages());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), pager.getCurrentPageItems());
        pager.goToPage(pager.getCurrentPage() + 1);
        assertEquals(List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20), pager.getCurrentPageItems());
        pager.goToPage(pager.getCurrentPage() + 1);
        assertEquals(List.of(21, 22, 23, 24, 25), pager.getCurrentPageItems()); // partial last page
    }

    @Test
    public void cannotGoPastEitherEnd() {
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(numbers(25));
        pager.goToPage(pager.getCurrentPage() - 1); // already on page 1
        assertEquals(1, pager.getCurrentPage());
        pager.goToPage(999);
        assertEquals(3, pager.getCurrentPage()); // clamped to the last real page
        pager.goToPage(pager.getCurrentPage() + 1); // already on the last page
        assertEquals(3, pager.getCurrentPage());
    }

    @Test
    public void emptyListIsOnePageWithNoItems() {
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(List.of());
        assertEquals(1, pager.getTotalPages());
        assertTrue(pager.getCurrentPageItems().isEmpty());
    }

    @Test
    public void nullItemsTreatedAsEmptyRatherThanThrowing() {
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(null);
        assertTrue(pager.getCurrentPageItems().isEmpty());
    }

    @Test
    public void shrinkingTheListPullsAnOutOfRangeCurrentPageBackIn() {
        // The real scenario: an admin is on page 3 of accounts, then deletes enough rows that
        // page 3 no longer exists - reloading the (now shorter) list must not leave the pager
        // pointing at a page with nothing to show.
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(numbers(25));
        pager.goToPage(3);
        pager.setItems(numbers(5));
        assertEquals(1, pager.getCurrentPage());
        assertEquals(5, pager.getCurrentPageItems().size());
    }

    @Test
    public void fromAndToIndexAreOneBasedForDisplay() {
        Pager<Integer> pager = new Pager<>(10);
        pager.setItems(numbers(25));
        pager.goToPage(2);
        assertEquals(11, pager.getFromIndex());
        assertEquals(20, pager.getToIndex());
        pager.goToPage(3);
        assertEquals(21, pager.getFromIndex());
        assertEquals(25, pager.getToIndex()); // clamped to the real count, not a full page
    }
}
