package co.rsk.core;

/**
 * Testing
 * This comment demonstrates the use of Javadoc comment.
 */
public class ExampleObject {
    private int number;
    private String text;

    public ExampleObject(int number, String text) {
        this.number = number;
        this.text = text;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "DummyClass{"
                + "number=" + number
                + ", text='" + text + '\''
                + '}';
    }

    /**
     * Example method def comment.
     */
    public int testDummyProcess(int a, int b) {
        int x = a + b;
        int help = x * 2;
        int test = help + 10;

        return test;
    }
}
