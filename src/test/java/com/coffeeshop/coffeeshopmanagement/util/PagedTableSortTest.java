package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The value ordering behind the sortable table headers (the JavaFX wiring itself needs a toolkit). */
public class PagedTableSortTest {

    @Test
    public void numbersSortNumericallyNotAsText() {
        assertTrue(PagedTable.compareValues(9, 10) < 0);
        assertTrue(PagedTable.compareValues(new BigDecimal("9000"), new BigDecimal("25000")) < 0);
    }

    @Test
    public void vietnameseTextSortsByLetterNotByCodePoint() {
        List<String> names = new ArrayList<>(List.of("Trà", "Cà phê", "Bánh ngọt", "Nước ép & Sinh tố"));
        names.sort(PagedTable::compareValues);
        assertEquals(List.of("Bánh ngọt", "Cà phê", "Nước ép & Sinh tố", "Trà"), names);
    }

    @Test
    public void accentedLetterStaysNextToItsBaseLetter() {
        assertTrue(PagedTable.compareValues("Ăn sáng", "Bún") < 0);
    }

    @Test
    public void nullsSortFirst() {
        assertTrue(PagedTable.compareValues(null, "a") < 0);
        assertTrue(PagedTable.compareValues("a", null) > 0);
        assertEquals(0, PagedTable.compareValues(null, null));
    }

    @Test
    public void datesSortChronologically() {
        assertTrue(PagedTable.compareValues(java.time.LocalDateTime.of(2026, 1, 31, 10, 0),
                java.time.LocalDateTime.of(2026, 2, 1, 9, 0)) < 0);
    }

    @Test
    public void sortingTheWholeListBeforePagingPutsTheGlobalMinimumOnPageOne() {
        List<Integer> all = new ArrayList<>(List.of(50, 7, 93, 12, 3, 88, 41, 65, 20, 1, 99, 34));
        all.sort(PagedTable::compareValues);
        Pager<Integer> pager = new Pager<>(5);
        pager.setItems(all);
        assertEquals(List.of(1, 3, 7, 12, 20), pager.getCurrentPageItems());
        pager.setPageSize(10);
        assertEquals(10, pager.getCurrentPageItems().size());
        assertEquals(2, pager.getTotalPages());
    }

    @Test
    public void changingPageSizeKeepsCurrentPageInRange() {
        Pager<Integer> pager = new Pager<>(2);
        pager.setItems(java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList());
        pager.goToPage(5);
        pager.setPageSize(100);
        assertEquals(1, pager.getCurrentPage());
        assertEquals(10, pager.getCurrentPageItems().size());
    }
}
