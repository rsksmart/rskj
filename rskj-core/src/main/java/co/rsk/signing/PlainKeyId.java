package co.rsk.signing;

/**
 * Represents a plain key id
 * given by a string.
 *
 * @author Ariel Mendelzon
 */
public class PlainKeyId implements KeyId {
    private final String id;

    public PlainKeyId(String id) {
        this.id = id;
    }

    @Override
    public boolean isPlainId() {
        return true;
    }

    @Override
    public String getPlainId() {
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

        return this.getPlainId().equals(((PlainKeyId) o).getPlainId());
    }
}
