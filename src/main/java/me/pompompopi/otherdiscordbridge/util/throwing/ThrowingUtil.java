package me.pompompopi.otherdiscordbridge.util.throwing;

import java.util.Optional;

public class ThrowingUtil {
  private ThrowingUtil() {}

  public static <T> Optional<T> supply(final ThrowingSupplier<T> supplier) {
    try {
      return Optional.ofNullable(supplier.get());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static void run(final ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Exception ignored) {}
  }
}
