package com.coffeeshop.coffeeshopmanagement.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs a blocking operation (a DB call, password hashing, etc.) on a background thread so it
 * never freezes the JavaFX Application Thread, then hands the result (or failure) back on the
 * FX thread automatically - callers never need to think about which thread they're on.
 *
 * This is a thin wrapper around {@link Task} rather than a new concurrency model: JavaFX
 * already guarantees onSucceeded/onFailed run on the FX thread, this just removes the
 * boilerplate of writing that Task every time.
 */
public final class Async {

    private Async() {
    }

    /** One background thread shared by every {@link #runOrdered} call, so those tasks run strictly in the order submitted. */
    private static final ExecutorService ORDERED = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "lunavera-ordered-task");
        thread.setDaemon(true);
        return thread;
    });

    /** Runs {@code backgroundWork} on its own new thread; tasks may run in any order relative to each other. */
    public static <T> void run(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = newTask(backgroundWork, onSuccess, onFailure);
        Thread thread = new Thread(task, "lunavera-async-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Like {@link #run}, but tasks submitted through this method execute one at a time in the order
     * they were submitted. Use it for writes that must not overtake each other (an autosave of the
     * cart must never land after the payment that follows it).
     */
    public static <T> void runOrdered(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        ORDERED.execute(newTask(backgroundWork, onSuccess, onFailure));
    }

    private static <T> Task<T> newTask(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundWork.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        return task;
    }
}
