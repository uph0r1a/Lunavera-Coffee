package com.coffeeshop.coffeeshopmanagement.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
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

    public static <T> void run(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundWork.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "lunavera-async-task");
        thread.setDaemon(true);
        thread.start();
    }
}
