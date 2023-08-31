package me.pompompopi.otherdiscordbridge.util.throwing;

public interface ThrowingBiFunction<T, U, R> {
  R accept(final T t, final U u) throws Exception;
}
