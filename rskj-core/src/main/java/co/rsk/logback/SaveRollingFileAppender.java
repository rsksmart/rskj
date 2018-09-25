package co.rsk.logback;

import ch.qos.logback.core.rolling.RollingFileAppender;

public class SaveRollingFileAppender<E> extends RollingFileAppender<E> {
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
