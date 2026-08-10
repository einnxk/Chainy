package dev.einnik.chainy.fallback;

import org.jspecify.annotations.NonNull;

/**
 * The synchronous fallback handler that is executer when a step
 * completely fails and no retries are available.
 *
 * @param <T> the type the step provides
 * @param <R> the type the step should return
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface SynchronousFallbackHandler<T, R> {
    R handle(@NonNull Throwable throwable, @NonNull T input) throws Exception;
}