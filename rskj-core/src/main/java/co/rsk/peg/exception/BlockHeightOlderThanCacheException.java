package co.rsk.peg.exception;

public class BlockHeightOlderThanCacheException extends IllegalStateException {
    public BlockHeightOlderThanCacheException() {
    }

    public BlockHeightOlderThanCacheException(String var1) {
        super(var1);
    }

    public BlockHeightOlderThanCacheException(String var1, Throwable var2) {
        super(var1, var2);
    }

    public BlockHeightOlderThanCacheException(Throwable var1) {
        super(var1);
    }
}
