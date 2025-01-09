package co.rsk.peg.federation;

public class ValueTracker<T> {
    private T value;
    private boolean modified = false;

    public boolean isModified() {
        return this.modified;
    }

    public T get() {
        return this.value;
    }

    public boolean isPresent() {
        return !isNull() || this.isModified();
    }

    public boolean isNull() {
        return this.value == null;
    }

    public void setNew(T aValue) {
        this.value = aValue;
        this.modified = true;
    }

    public void set(T aValue) {
        this.value = aValue;
    }
}
