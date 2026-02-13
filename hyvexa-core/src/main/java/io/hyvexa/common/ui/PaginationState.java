package io.hyvexa.common.ui;

public class PaginationState {

    private final int pageSize;
    private int pageIndex;

    public PaginationState(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
    }

    public void next() {
        pageIndex++;
    }

    public void previous() {
        if (pageIndex > 0) {
            pageIndex--;
        }
    }

    public void reset() {
        pageIndex = 0;
    }

    public PageSlice slice(int totalEntries) {
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) pageSize));
        int clampedIndex = pageIndex;
        if (clampedIndex < 0) {
            clampedIndex = 0;
        } else if (clampedIndex >= totalPages) {
            clampedIndex = totalPages - 1;
        }
        int startIndex = clampedIndex * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalEntries);
        return new PageSlice(startIndex, endIndex, clampedIndex, totalPages);
    }

    public static class PageSlice {
        public final int startIndex;
        public final int endIndex;
        public final int pageIndex;
        public final int totalPages;

        private PageSlice(int startIndex, int endIndex, int pageIndex, int totalPages) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.pageIndex = pageIndex;
            this.totalPages = totalPages;
        }

        public String getLabel() {
            return "Page " + (pageIndex + 1) + "/" + totalPages;
        }
    }
}
