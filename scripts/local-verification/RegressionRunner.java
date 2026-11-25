package local.hyinit.verify;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public final class RegressionRunner {
    public static void main(String[] tests) {
        for (String test : tests) {
            String[] parts = test.split(":");
            String label = String.join("-", parts).substring(parts[0].lastIndexOf('.') + 1);
            System.out.println("=== " + label);
            System.out.flush();
            try {
                Class.forName(parts[0])
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) Arrays.copyOfRange(parts, 1, parts.length));
            } catch (Throwable failure) {
                Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
                System.out.flush();
                System.err.println("FAIL " + label);
                cause.printStackTrace();
                System.exit(1);
            }
            System.out.println("=== " + label + " passed");
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
