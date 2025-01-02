package co.rsk.peg.federation;

public abstract class FederationTracker<T> {
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

    public void setNew(T aFederation) {
        this.federation = aFederation;
        this.modified = true;
    }

    public void replace(T aFederation) {
        this.federation = aFederation;
    }
}
