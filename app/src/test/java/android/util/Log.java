package android.util;

/**
 * JVM-only log sink for MoneyTalkLogger. Local parser tests exercise failure
 * branches without the Android runtime; logging must not replace their results
 * with android.jar's "not mocked" exception.
 */
public final class Log {
    private Log() {
    }

    public static int d(String tag, String message) {
        return 0;
    }

    public static int i(String tag, String message) {
        return 0;
    }

    public static int w(String tag, String message) {
        return 0;
    }

    public static int w(String tag, String message, Throwable throwable) {
        return 0;
    }

    public static int e(String tag, String message) {
        return 0;
    }

    public static int e(String tag, String message, Throwable throwable) {
        return 0;
    }
}
