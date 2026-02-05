package io.hyvexa.ascend.ui;

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
        if (pageIndex < 0) {
            pageIndex = 0;
        } else if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        int startIndex = pageIndex * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalEntries);
        return new PageSlice(startIndex, endIndex, pageIndex, totalPages);
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
