package dev.einnik.chainy.fallback;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The asynchronous fallback handler that is executer when a step
 * completely fails and no retries are available.
 *
 * @param <T> the type the step provides
 * @param <R> the type the step should return wrapped into an
 *           {@link CompletableFuture}
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface AsynchronousFallbackHandler<T, R> {
    @NonNull CompletableFuture<R> handle(@NonNull Throwable cause, @NonNull T input) throws CompletionException;
}