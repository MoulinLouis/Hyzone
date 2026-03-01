package io.hyvexa.common.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaginationStateTest {

    @Test
    void sliceFirstPage() {
        PaginationState ps = new PaginationState(10);
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals(0, slice.startIndex);
        assertEquals(10, slice.endIndex);
        assertEquals(0, slice.pageIndex);
        assertEquals(3, slice.totalPages);
    }

    @Test
    void sliceSecondPage() {
        PaginationState ps = new PaginationState(10);
        ps.next();
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals(10, slice.startIndex);
        assertEquals(20, slice.endIndex);
        assertEquals(1, slice.pageIndex);
    }

    @Test
    void sliceLastPagePartial() {
        PaginationState ps = new PaginationState(10);
        ps.next();
        ps.next();
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals(20, slice.startIndex);
        assertEquals(25, slice.endIndex);
        assertEquals(2, slice.pageIndex);
    }

    @Test
    void sliceClampsOverflowedPageIndex() {
        PaginationState ps = new PaginationState(10);
        for (int i = 0; i < 20; i++) ps.next();
        PaginationState.PageSlice slice = ps.slice(25);
        // Should clamp to last page (index 2)
        assertEquals(2, slice.pageIndex);
        assertEquals(20, slice.startIndex);
        assertEquals(25, slice.endIndex);
    }

    @Test
    void previousDoesNotGoBelowZero() {
        PaginationState ps = new PaginationState(10);
        ps.previous();
        ps.previous();
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals(0, slice.pageIndex);
    }

    @Test
    void nextAndReset() {
        PaginationState ps = new PaginationState(10);
        ps.next();
        ps.next();
        ps.reset();
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals(0, slice.pageIndex);
    }

    @Test
    void getLabel() {
        PaginationState ps = new PaginationState(10);
        ps.next();
        PaginationState.PageSlice slice = ps.slice(25);
        assertEquals("Page 2/3", slice.getLabel());
    }

    @Test
    void pageSizeClampedToOne() {
        PaginationState ps = new PaginationState(0);
        PaginationState.PageSlice slice = ps.slice(5);
        // pageSize=1, so 5 pages
        assertEquals(5, slice.totalPages);
        assertEquals(0, slice.startIndex);
        assertEquals(1, slice.endIndex);
    }

    @Test
    void negativePagesizeClampedToOne() {
        PaginationState ps = new PaginationState(-5);
        PaginationState.PageSlice slice = ps.slice(3);
        assertEquals(3, slice.totalPages);
    }

    @Test
    void zeroTotalEntriesGivesOnePage() {
        PaginationState ps = new PaginationState(10);
        PaginationState.PageSlice slice = ps.slice(0);
        assertEquals(1, slice.totalPages);
        assertEquals(0, slice.startIndex);
        assertEquals(0, slice.endIndex);
        assertEquals("Page 1/1", slice.getLabel());
    }
}
