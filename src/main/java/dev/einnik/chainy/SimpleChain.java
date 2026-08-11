package dev.einnik.chainy;

import dev.einnik.chainy.callback.*;
import dev.einnik.chainy.fallback.AsynchronousFallbackHandler;
import dev.einnik.chainy.fallback.SynchronousFallbackHandler;
import dev.einnik.chainy.function.AsyncChainyFunction;
import dev.einnik.chainy.function.ChainyConsumer;
import dev.einnik.chainy.function.ChainyFunction;
import dev.einnik.chainy.function.ChainySupplier;
import dev.einnik.chainy.internal.StepConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public final class SimpleChain<T> implements Chain<T> {

    private final Function<Executor, CompletableFuture<T>> pipeline;
    private final StepConfig<?, T> currentConfig;

    public static @NonNull <R> Chain<R> init(int stepCount, @NonNull ChainySupplier<R> supplier) {
        StepConfig<Void, R> config = new StepConfig<>(stepCount);
        Function<Executor, CompletableFuture<R>> pipeline = executor ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor);

        return new SimpleChain<>(pipeline, config);
    }

    public static @NonNull <R> Chain<R> initAsync(int stepCount, @NonNull ChainySupplier<CompletableFuture<R>> supplier) {
        StepConfig<Void, R> config = new StepConfig<>(stepCount);
        Function<Executor, CompletableFuture<R>> pipeline = executor ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor).thenCompose(Function.identity());

        return new SimpleChain<>(pipeline, config);
    }

    @Override
    public @NonNull <R> Chain<R> then(int stepCount, @NonNull ChainyFunction<T, R> function) {
        final StepConfig<T, R> nextConfig = new StepConfig<>(stepCount);

        Function<Executor, CompletableFuture<R>> pipeline = executor ->
                this.pipeline.apply(executor).thenApplyAsync(input -> {
                    try {
                        return function.apply(input);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor);

        return new SimpleChain<>(pipeline, nextConfig);
    }

    @Override
    public @NonNull <R> Chain<R> thenAsynchronously(int stepCount, @NonNull AsyncChainyFunction<T, R> function) {
        final StepConfig<T, R> nextConfig = new StepConfig<>(stepCount);

        Function<Executor, CompletableFuture<R>> pipeline = executor ->
                this.pipeline.apply(executor).thenComposeAsync(input -> {
                    try {
                        return function.apply(input);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                }, executor);

        return new SimpleChain<>(pipeline, nextConfig);
    }

    @Override
    public @NonNull Chain<T> peek(int stepCount, @NonNull ChainyConsumer<T> consumer) {
        return then(stepCount, input -> {
            consumer.accept(input);
            return input;
        });
    }

    @Override
    public @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> function) {
        return then(Integer.MIN_VALUE, input -> {
            if (predicate.test(input)) {
                return function.apply(input);
            }

            return input;
        });
    }

    @Override
    public @NonNull Chain<T> thenIf(@NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> ifFunction, @NonNull ChainyFunction<T, T> elseFunction) {
        return then(Integer.MAX_VALUE, input -> {
            if (predicate.test(input)) {
                return ifFunction.apply(input);
            } else {
                return elseFunction.apply(input);
            }
        });
    }

    @Override
    public @NonNull Chain<T> delay(@NonNull Duration delay) {
        return thenAsynchronously(Integer.MAX_VALUE, input ->
                CompletableFuture.supplyAsync(() -> input,
                        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)));
    }

    @Override
    public @NonNull <R> Chain<List<R>> thenAll(@NonNull List<AsyncChainyFunction<T, R>> asyncChainyFunctions) {
        return thenAsynchronously(Integer.MAX_VALUE, input -> {
            final List<CompletableFuture<R>> futures = new ArrayList<>();

            for (AsyncChainyFunction<T, R> function : asyncChainyFunctions) {
                try {
                    futures.add(function.apply(input));
                } catch (Exception e) {
                    futures.add(CompletableFuture.failedFuture(e));
                }
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());
        });
    }

    @Override
    public @NonNull Chain<T> withRetry(int maxAttempts, Duration delay) {
        this.currentConfig.maxAttempts(maxAttempts);
        this.currentConfig.delay(delay);
        return this;
    }

    @Override
    public @NonNull Chain<T> withRetry(int maxAttempts, Duration delay, RetryPredicateCallback predicate) {
        this.currentConfig.maxAttempts(maxAttempts);
        this.currentConfig.delay(delay);
        this.currentConfig.retryPredicate(predicate);
        return this;
    }

    @Override
    public @NonNull Chain<T> withTimeout(Duration duration) {
        this.currentConfig.timeout(duration);
        return this;
    }

    @Override
    public @NonNull Chain<T> onErrorFallback(SynchronousFallbackHandler<T, T> fallback) {
        //TODO
        return this;
    }

    @Override
    public @NonNull Chain<T> onErrorFallbackAsync(AsynchronousFallbackHandler<T, T> fallbackHandler) {
        //TODO
        return this;
    }

    @Override
    public @NonNull Chain<T> onErrorResume(Function<Throwable, T> fallbackValue) {
        return onErrorFallback(((cause, _) -> fallbackValue.apply(cause)));
    }

    @Override
    public @NonNull Chain<T> onStart(StepStartCallback<Object> hook) {
        //TODO
        return this;
    }

    @Override
    public @NonNull Chain<T> onSuccess(StepSuccessCallback<Object, Object> hook) {
        //TODO
        return this;
    }

    @Override
    public @NonNull Chain<T> onFailure(StepFailureCallback<Object> hook) {
        //TODO
        return this;
    }

    @Override
    public @NonNull Chain<T> onRetry(StepRetryCallback hook) {
        //TODO
        return this;
    }

    @Override
    public @NonNull CompletableFuture<T> asynchronously() {
        return asynchronously(ForkJoinPool.commonPool());
    }

    @Override
    public @NonNull CompletableFuture<T> asynchronously(@NonNull Executor executor) {
        return pipeline.apply(executor);
    }

    @Override
    public @NonNull T blocking() {
        try {
            return asynchronously().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Chain interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NonNull T blocking(@NonNull Duration timeout) {
        try {
            return asynchronously().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Chain timed out after " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Chain interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}