package co.rsk.peg.federation;

public class FederationTracker<T> {
    private T federation;
    private boolean modified = false;

    public boolean isModified() {
        return this.modified;
    }

    public T get() {
        return this.federation;
    }

    public boolean isPresent() {
        return this.federation != null;
    }

    public boolean hasBeenSet() {
        return this.isPresent() || this.isModified();
    }

    public void set(T aFederation, boolean shouldSave) {
        this.federation = aFederation;
        this.modified = shouldSave;
    }
}
