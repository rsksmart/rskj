package co.rsk.signing;

/**
 * Represents a basic key id
 * given by a string.
 * This can be extended to provide
 * additional identification
 * capabilities for specific
 * forms of key searching
 * (e.g., HD wallet path, public key, etc.)
 *
 * @author Ariel Mendelzon
 */
public class KeyId {
    private final String id;

    public KeyId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        return this.getId().equals(((KeyId) o).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return String.format("<%s>", getId());
    }
}
