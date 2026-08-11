package dev.einnik.chainy;

import dev.einnik.chainy.callback.*;
import dev.einnik.chainy.fallback.AsynchronousFallbackHandler;
import dev.einnik.chainy.fallback.SynchronousFallbackHandler;
import dev.einnik.chainy.function.AsyncChainyFunction;
import dev.einnik.chainy.function.ChainyConsumer;
import dev.einnik.chainy.function.ChainyFunction;
import dev.einnik.chainy.function.ChainySupplier;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A chain is the core model class which represents the builder
 * of a workflow with all the execution steps and the async or
 * sync execution which returns the final type.
 *
 * @param <T>
 * @author EinNik
 * @since 1.0.0
 */
public interface Chain<T> {

    /**
     * Start a chain based of a synchronous supplier.
     *
     * @param supplier the supplier of the start value
     * @param <R> the return type of the start value
     * @return an instance of a {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWith(@NonNull ChainySupplier<R> supplier) {
        return startWith(0, supplier);
    }

    /**
     * Start a chain based of a synchronous supplier and the additional execution order count.
     *
     * @param stepCount the execution order count
     * @param supplier the supplier of the start value
     * @param <R> the return type of the start value
     * @return an instance of a {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWith(int stepCount, @NonNull ChainySupplier<R> supplier) {
        return SimpleChain.init(stepCount, supplier);
    }

    /**
     * Start a chain based opf an asynchronous supplier and the addition of an execution order.
     *
     * @param stepCount the execution order count
     * @param supplier the supplier of the start value
     * @param <R> the return type of the start value
     * @return an instance of a {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWithAsynchronously(int stepCount, @NonNull ChainySupplier<CompletableFuture<R>> supplier) {
        return SimpleChain.initAsync(stepCount, supplier);
    }

    /**
     * Start from fixed value.
     *
     * @param initialValue the initial fixed value
     * @param <R> the return type of the start value
     * @return an instance of a {@link Chain} builder
     */
    static @NonNull <R> Chain<R> from(@NonNull R initialValue) {
        return startWith(0, () -> initialValue);
    }

    /**
     * Chain another synchronous request to the {@link Chain}.
     *
     * @param stepCount the execution order count
     * @param function the sync function that should be executed next
     * @param <R> the return type of the latest value
     * @return an instance of a {@link Chain} builder
     */
    @NonNull <R> Chain<R> then(int stepCount, @NonNull ChainyFunction<T, R> function);

    /**
     * Chain another asynchronous request to the {@link Chain}.
     *
     * @param stepCount the execution order count
     * @param function the async function that should be executed next
     * @param <R> the return type of the latest value
     * @return an instance of a {@link Chain} builder
     */
    @NonNull <R> Chain<R> thenAsynchronously(int stepCount, @NonNull AsyncChainyFunction<T, R> function);

    /**
     * Interact with the latest return value using an {@link ChainyConsumer} to modify it, after
     * the consumer is accepted, the next step is executed.
     *
     * @param stepCount the execution order count
     * @param consumer the consumer with the action that should be performed
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> peek(int stepCount, @NonNull ChainyConsumer<T> consumer);

    /**
     * Chain another {@link ChainyFunction} under the condition of a {@link Predicate} to
     * the {@link Chain}.
     *
     * @param predicate the condition that should decide if the function is chained
     * @param function the function that is executed if the predicate value is true
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> function);

    /**
     * Chain another if-else {@link ChainyFunction} under the condition of a {@link Predicate}
     * to the {@link Chain}.
     *
     * @param predicate the condition that should decide if the ifFunction is chained
     * @param ifFunction the function that is executed if the predicate value is fulfilled
     * @param elseFunction the function that is executed if the predicate value is not fulfilled
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> ifFunction, @NonNull ChainyFunction<T, T> elseFunction);

    /**
     * Wait until the next step this {@link Duration} of time.
     *
     * @param delay the time until the next step
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> delay(@NonNull Duration delay);

    /**
     * Executes multiple {@link AsyncChainyFunction} concurrently and collects them into an
     * {@link java.util.Collection}.
     *
     * @param functions a list of all the functions that should be executed concurrent
     * @param <R> the return type of the latest value
     * @return an instance of a {@link Chain} builder
     */
    @NonNull <R> Chain<List<R>> thenAll(@NonNull List<AsyncChainyFunction<T, R>> functions);

    /**
     * Define a maximum amounts of tries the {@link Chain} is executed, with a timeout
     * in between.
     *
     * @param maxAttempts the total amount of attempts
     * @param delay the timeout between attempts
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> withRetry(int maxAttempts, Duration delay);

    /**
     * Define a maximum amounts of tries the {@link Chain} is executed, with a timeout
     * in between and register an {@link RetryPredicateCallback} to perform an action
     * after all tries have failed.
     *
     * @param maxAttempts the total amount of attempts
     * @param delay the timeout between attempts
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> withRetry(int maxAttempts, Duration delay, RetryPredicateCallback predicate);

    /**
     * Define a maximum timeout for the chain to complete.
     *
     * @param duration the duration the {@link Chain} must be completed in
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> withTimeout(Duration duration);

    /**
     * Register a callback that executes an action when all tries have failed and the
     * chain is about to fail.
     *
     * @param fallback the sync handler that is invoked after all tries have failed
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onErrorFallback(SynchronousFallbackHandler<T, T> fallback);

    /**
     * Register a callback that executes an action when all tries have failed and the
     * chain is about to fail.
     *
     * @param fallbackHandler the async handler that is invoked after all tries have failed
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onErrorFallbackAsync(AsynchronousFallbackHandler<T, T> fallbackHandler);

    /**
     * Register a callback that executes a step has failed and a new step is executed next.
     *
     * @param fallbackValue the fallback value
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onErrorResume(Function<Throwable, T> fallbackValue);

    /**
     * Register a callback that is started when a new step is executed.
     *
     * @param hook the callback functional interface that is invoked
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onStart(StepStartCallback<Object> hook);

    /**
     * Register a callback that is executed when a step has been successfully
     * executed and the next step is preparing to execute.
     *
     * @param hook the callback functional interface that is invoked
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onSuccess(StepSuccessCallback<Object, Object> hook);

    /**
     * Register a callback that is executed when a step has failed to execute.
     *
     * @param hook the callback functional interface that is invoked
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onFailure(StepFailureCallback<Object> hook);

    /**
     * Register a callback that is executed when a step has been failed and a new
     * retry has been started.
     *
     * @param hook the callback functional interface that is invoked
     * @return an instance of a {@link Chain} builder
     */
    @NonNull Chain<T> onRetry(StepRetryCallback hook);

    /**
     * Execute the {@link Chain} asynchronously without any maximum time until timeout.
     * <p>
     * The threads that are used for asynchronous execution are retrieved from a virtual
     * thread pool in modern java version.
     *
     * @return the value of the chains final step wrapped into an {@link CompletableFuture}
     */
    @NonNull CompletableFuture<T> asynchronously();

    /**
     * Execute the {@link Chain} asynchronously without any maximum time until timeout, and
     * an additional provided {@link Executor}.
     *
     * @param executor the executer with are used for the asynchronous execution
     * @return the value of the chains final step wrapped into an {@link CompletableFuture}
     */
    @NonNull CompletableFuture<T> asynchronously(@NonNull Executor executor);

    /**
     * Execute the {@link Chain} synchronously without any maximum time until timeout.
     *
     * @deprecated Invoke this method with caution. This can block the {@link Thread} permanently,
     *             so do not execute on your programs main thread.
     * @return the value of the chains final step
     */
    @Deprecated
    @NonNull T blocking();

    /**
     * Execute the {@link Chain} synchronously with a maximum amount of time that can
     * pass until the chain is fulfilled.
     *
     * @param timeout the time the chain can max take
     * @return the value of the chains final step
     */
    @NonNull T blocking(@NonNull Duration timeout);
}