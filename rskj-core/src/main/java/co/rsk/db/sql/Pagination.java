package co.rsk.db.sql;

public class Pagination<E> {
    private E content;
    private long totalCount;
    private int pageSize;
    private int page;

    public Pagination() {
    }

    public Pagination(E content, long totalCount, int pageSize, int page) {
        this.content = content;
        this.totalCount = totalCount;
        this.pageSize = pageSize;
        this.page = page;
    }

    public E getContent() {
        return content;
    }

    public void setContent(E content) {
        this.content = content;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public double getTotalPages() {
        return Math.ceil((double) totalCount / pageSize);
    }
}
