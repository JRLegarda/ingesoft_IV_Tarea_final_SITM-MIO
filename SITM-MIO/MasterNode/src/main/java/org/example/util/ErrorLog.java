package org.example.util;

public final class ErrorLog {

    private ErrorLog() {
    }

    public static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }

        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) {
                builder.append(" | caused by ");
            }
            builder.append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(": ").append(message);
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    public static String topStack(Throwable throwable) {
        if (throwable == null || throwable.getStackTrace().length == 0) {
            return "no-stack";
        }

        StackTraceElement top = throwable.getStackTrace()[0];
        return top.getClassName() + "." + top.getMethodName() + ":" + top.getLineNumber();
    }
}
