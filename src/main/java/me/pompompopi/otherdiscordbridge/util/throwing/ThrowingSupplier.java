package me.pompompopi.otherdiscordbridge.util.throwing;

public interface ThrowingSupplier<T> {
  T get() throws Exception;
}
