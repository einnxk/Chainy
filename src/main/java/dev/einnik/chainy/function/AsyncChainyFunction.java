package dev.einnik.chainy.function;

import java.util.concurrent.CompletableFuture;

/**
 * An asynchronously wrapped form of the {@link java.util.function.Function} functional interface,
 * which allows checked interfaces.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function wrapped into an {@link CompletableFuture}
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface AsyncChainyFunction<T, R> {
    CompletableFuture<R> apply(T input) throws Exception;
}