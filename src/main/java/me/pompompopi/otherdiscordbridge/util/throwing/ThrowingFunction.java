package me.pompompopi.otherdiscordbridge.util.throwing;

public interface ThrowingFunction<T, R> {
  R apply(final T t) throws Exception;
}
