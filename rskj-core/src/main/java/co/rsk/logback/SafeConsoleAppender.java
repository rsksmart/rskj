package co.rsk.logback;

import ch.qos.logback.core.ConsoleAppender;

public class SafeConsoleAppender<E> extends ConsoleAppender<E> {
    @Override
    protected void subAppend(E event) {
        try {
            super.subAppend(event);
        }
        catch (Throwable ex) {
            addError(ex.getMessage());
        }
    }
}
