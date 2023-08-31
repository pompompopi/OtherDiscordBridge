package me.pompompopi.otherdiscordbridge.executor;

import java.util.concurrent.RejectedExecutionException;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.jetbrains.annotations.NotNull;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

public final class MainThreadScheduler implements Scheduler {
  private final MinecraftDedicatedServer dedicatedServer;

  public MainThreadScheduler(final MinecraftDedicatedServer dedicatedServer) {
    this.dedicatedServer = dedicatedServer;
  }

  @Override
  public Disposable schedule(final @NotNull Runnable task) {
    final MainThreadTask mainThreadTask = new MainThreadTask(task);
    this.dedicatedServer.execute(mainThreadTask);
    return mainThreadTask;
  }

  @Override
  public Worker createWorker() {
    return new MainThreadWorker(this.dedicatedServer);
  }

  public static final class MainThreadTask implements Disposable, Runnable {
    private final Runnable task;
    private volatile boolean disposed;

    public MainThreadTask(final Runnable task) {
      this.task = task;
    }

    @Override
    public void dispose() {
      this.disposed = true;
    }

    @Override
    public void run() {
      if (this.disposed) {
        return;
      }

      this.task.run();
    }
  }

  public static final class MainThreadWorker implements Worker {
    private final MinecraftDedicatedServer dedicatedServer;
    private volatile boolean disposed;

    public MainThreadWorker(final MinecraftDedicatedServer dedicatedServer) {
      this.dedicatedServer = dedicatedServer;
    }

    @Override
    public Disposable schedule(final @NotNull Runnable task) {
      if (this.disposed) {
       throw new RejectedExecutionException();
      }

      final MainThreadTask mainThreadTask = new MainThreadTask(task);
      this.dedicatedServer.execute(mainThreadTask);
      return mainThreadTask;
    }

    @Override
    public void dispose() {
      this.disposed = true;
    }
  }
}
