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
 * A {@code Chain} describes a workflow of steps that each consume the return
 * value of the step before them and produce the input for the step after
 * them.
 * <p>
 * <b>Sync vs. async is not a property of the builder.</b> A chain built with
 * {@link #then(int, ChainyFunction)} and one built with
 * {@link #thenAsynchronously(int, AsyncChainyFunction)} produce the exact
 * same {@code Chain<R>} type and can be freely mixed. Every step, whether
 * defined synchronously or asynchronously, is capable of running in either
 * mode. Which mode is actually used is decided once, at the very end, by
 * calling either {@link #blocking()}/{@link #blocking(Duration)} or
 * {@link #asynchronously()}/{@link #asynchronously(Executor)}. A step
 * originally defined with {@code then} that ends up inside an
 * {@code asynchronously()} execution is dispatched onto the executor; a step
 * originally defined with {@code thenAsynchronously} that ends up inside a
 * {@code blocking()} execution is simply joined.
 *
 * @param <T> the type of the value produced by the latest step
 * @author EinNik
 * @since 1.0.0
 */
public interface Chain<T> {

    /**
     * Starts a chain from a synchronous supplier of the initial value.
     *
     * @param supplier the supplier producing the start value
     * @param <R> the type of the start value
     * @return a new {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWith(@NonNull ChainySupplier<R> supplier) {
        return startWith(0, supplier);
    }

    /**
     * Starts a chain from a synchronous supplier of the initial value.
     *
     * @param stepCount the execution order assigned to this step
     * @param supplier the supplier producing the start value
     * @param <R> the type of the start value
     * @return a new {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWith(int stepCount, @NonNull ChainySupplier<R> supplier) {
        return null;
    }

    /**
     * Starts a chain from an asynchronous supplier of the initial value.
     *
     * @param stepCount the execution order assigned to this step
     * @param supplier the supplier producing the start value
     * @param <R> the type of the start value
     * @return a new {@link Chain} builder
     */
    static @NonNull <R> Chain<R> startWithAsynchronously(int stepCount, @NonNull ChainySupplier<CompletableFuture<R>> supplier) {
        return null;
    }

    /**
     * Starts a chain from a value that is already available.
     *
     * @param initialValue the fixed start value
     * @param <R> the type of the start value
     * @return a new {@link Chain} builder
     */
    static @NonNull <R> Chain<R> from(@NonNull R initialValue) {
        return startWith(0, () -> initialValue);
    }

    /**
     * Chains a synchronous step that consumes the current value {@code T}
     * and produces a new value {@code R}.
     *
     * @param stepCount the execution order assigned to this step
     * @param function the function receiving the previous step's return value
     * @param <R> the type produced by this step
     * @return the same chain, now producing {@code R}
     */
    @NonNull <R> Chain<R> then(int stepCount, @NonNull ChainyFunction<T, R> function);

    /**
     * Chains an asynchronous step that consumes the current value {@code T}
     * and produces a new value {@code R}. Behaves identically to
     * {@link #then(int, ChainyFunction)} once the chain is executed — see
     * the class-level documentation for how the execution mode is resolved.
     *
     * @param stepCount the execution order assigned to this step
     * @param function the function receiving the previous step's return value
     * @param <R> the type produced by this step
     * @return the same chain, now producing {@code R}
     */
    @NonNull <R> Chain<R> thenAsynchronously(int stepCount, @NonNull AsyncChainyFunction<T, R> function);

    /**
     * Passes the current value to a consumer without changing it, then
     * continues the chain with that same value.
     *
     * @param stepCount the execution order assigned to this step
     * @param consumer the consumer inspecting or acting on the current value
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> peek(int stepCount, @NonNull ChainyConsumer<T> consumer);

    /**
     * Chains a step that is only applied if {@code predicate} holds for the
     * current value; otherwise the value is passed through unchanged.
     *
     * @param predicate the condition deciding whether {@code function} runs
     * @param function the function applied if {@code predicate} is fulfilled
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> function);

    /**
     * Chains an if/else step: {@code ifFunction} is applied if
     * {@code predicate} holds for the current value, otherwise
     * {@code elseFunction} is applied.
     *
     * @param predicate the condition deciding which function runs
     * @param ifFunction the function applied if {@code predicate} is fulfilled
     * @param elseFunction the function applied if {@code predicate} is not fulfilled
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> ifFunction, @NonNull ChainyFunction<T, T> elseFunction);

    /**
     * Waits the given duration before continuing with the next step. The
     * current value is passed through unchanged.
     *
     * @param delay the time to wait before the next step
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> delay(@NonNull Duration delay);

    /**
     * Runs several asynchronous functions concurrently against the current
     * value and collects their results, in order, into a single list.
     *
     * @param functions the functions to run concurrently
     * @param <R> the element type produced by each function
     * @return the same chain, now producing {@code List<R>}
     */
    @NonNull <R> Chain<List<R>> thenAll(@NonNull List<AsyncChainyFunction<T, R>> functions);

    /**
     * Re-executes the whole chain from the start-up to {@code maxAttempts}
     * times if a step throws, waiting {@code delay} between attempts.
     *
     * @param maxAttempts the total number of attempts, including the first
     * @param delay the wait time between attempts
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> withRetry(int maxAttempts, Duration delay);

    /**
     * Same as {@link #withRetry(int, Duration)}, but additionally consults
     * {@code predicate} after each failed attempt to decide whether another
     * attempt should be made at all.
     *
     * @param maxAttempts the total number of attempts, including the first
     * @param delay the wait time between attempts
     * @param predicate decides, per failed attempt, whether to retry further
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> withRetry(int maxAttempts, Duration delay, RetryPredicateCallback predicate);

    /**
     * Fails the chain if it has not completed within {@code duration},
     * regardless of how many retry attempts to remain.
     *
     * @param duration the maximum duration the chain may take
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> withTimeout(Duration duration);

    /**
     * Registers a synchronous fallback that is invoked once every retry
     * attempt has been exhausted, producing a substitute result instead of
     * failing the chain.
     *
     * @param fallback the handler invoked after all attempts have failed
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onErrorFallback(SynchronousFallbackHandler<T, T> fallback);

    /**
     * Same as {@link #onErrorFallback(SynchronousFallbackHandler)}, but the
     * fallback itself produces its result asynchronously.
     *
     * @param fallbackHandler the handler invoked after all attempts have failed
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onErrorFallbackAsync(AsynchronousFallbackHandler<T, T> fallbackHandler);

    /**
     * Registers a fallback that resumes the chain with a substitute value
     * whenever a single step fails, without affecting retry accounting.
     *
     * @param fallbackValue produces the substitute value from the thrown error
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onErrorResume(Function<Throwable, T> fallbackValue);

    /**
     * Registers a hook invoked right before a step starts executing.
     *
     * @param hook the callback to invoke
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onStart(StepStartCallback<Object> hook);

    /**
     * Registers a hook invoked right after a step completes successfully,
     * before the next step starts.
     *
     * @param hook the callback to invoke
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onSuccess(StepSuccessCallback<Object, Object> hook);

    /**
     * Registers a hook invoked when a step fails to execute.
     *
     * @param hook the callback to invoke
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onFailure(StepFailureCallback<Object> hook);

    /**
     * Registers a hook invoked whenever a failed step triggers a new
     * retry attempt.
     *
     * @param hook the callback to invoke
     * @return the same chain, still producing {@code T}
     */
    @NonNull Chain<T> onRetry(StepRetryCallback hook);

    /**
     * Executes the chain asynchronously, without a timeout unless
     * {@link #withTimeout(Duration)} was configured. Steps that were
     * defined synchronously are dispatched onto a virtual-thread pool.
     *
     * @return the final step's value, wrapped in a {@link CompletableFuture}
     */
    @NonNull CompletableFuture<T> asynchronously();

    /**
     * Same as {@link #asynchronously()}, but synchronous steps are
     * dispatched onto the given executor instead of the default
     * virtual-thread pool.
     *
     * @param executor the executor used for steps defined synchronously
     * @return the final step's value, wrapped in a {@link CompletableFuture}
     */
    @NonNull CompletableFuture<T> asynchronously(@NonNull Executor executor);

    /**
     * Executes the chain synchronously on the calling thread, without a
     * timeout unless {@link #withTimeout(Duration)} was configured. Steps
     * that were defined asynchronously are joined.
     *
     * @deprecated blocks the calling {@link Thread} until the chain
     *             completes — never call this on your program's main thread.
     * @return the final step's value
     */
    @Deprecated
    @NonNull T blocking();

    /**
     * Same as {@link #blocking()}, but fails if the chain has not
     * completed within {@code timeout}.
     *
     * @param timeout the maximum duration the chain may take
     * @return the final step's value
     */
    @NonNull T blocking(@NonNull Duration timeout);
}