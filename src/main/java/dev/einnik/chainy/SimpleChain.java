/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.einnik.chainy;

import dev.einnik.chainy.callback.*;
import dev.einnik.chainy.fallback.AsynchronousFallbackHandler;
import dev.einnik.chainy.fallback.SynchronousFallbackHandler;
import dev.einnik.chainy.function.AsyncChainyFunction;
import dev.einnik.chainy.function.ChainyConsumer;
import dev.einnik.chainy.function.ChainyFunction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class SimpleChain<T> implements Chain<T> {

  private static final Executor DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final List<Step<Object, Object>> steps;

  private int maxAttempts = 1;
  private Duration retryDelay = Duration.ZERO;
  private RetryPredicateCallback retryPredicate;
  private Duration timeout;

  private SynchronousFallbackHandler<Object, Object> synchronousFallbackHandler;
  private AsynchronousFallbackHandler<Object, Object> asynchronousFallbackHandler;
  private Function<Throwable, Object> resumeFallback;

  private StepStartCallback<Object> onStart;
  private StepSuccessCallback<Object, Object> onSuccess;
  private StepFailureCallback<Object> onFailure;
  private StepRetryCallback onRetry;

  public static <R> @NonNull SimpleChain<R> ofStep(@NonNull Step<Void, R> firstStep) {
    List<Step<Object, Object>> steps = new ArrayList<>();
    steps.add((Step<Object, Object>) (Step<?, ?>) firstStep);
    return new SimpleChain<>(steps);
  }

  private <R> @NonNull SimpleChain<R> append(@NonNull Step<?, ?> step) {
    steps.add((Step<Object, Object>) step);
    return (SimpleChain<R>) this;
  }

  @Override
  public @NonNull <R> Chain<R> then(int stepCount, @NonNull ChainyFunction<T, R> function) {
    return append(Step.ofSync(stepCount, function));
  }

  @Override
  public @NonNull <R> Chain<R> thenAsynchronously(
      int stepCount, @NonNull AsyncChainyFunction<T, R> function) {
    return append(Step.ofAsync(stepCount, function));
  }

  @Override
  public @NonNull Chain<T> peek(int stepCount, @NonNull ChainyConsumer<T> consumer) {
    return append(Step.ofPeek(stepCount, consumer));
  }

  @Override
  public @NonNull Chain<T> thenIf(
      @NonNull Predicate<T> predicate, @NonNull ChainyFunction<T, T> function) {
    return thenIf(predicate, function, value -> value);
  }

  @Override
  public @NonNull Chain<T> thenIf(
      @NonNull Predicate<T> predicate,
      @NonNull ChainyFunction<T, T> ifFunction,
      @NonNull ChainyFunction<T, T> elseFunction) {
    return append(Step.ofConditional(nextStepCount(), predicate, ifFunction, elseFunction));
  }

  @Override
  public @NonNull Chain<T> delay(@NonNull Duration delay) {
    return append(Step.ofDelay(nextStepCount(), delay));
  }

  @Override
  public @NonNull <R> Chain<List<R>> thenAll(
      @NonNull List<AsyncChainyFunction<T, R>> asyncChainyFunctions) {
    return append(Step.ofAll(nextStepCount(), asyncChainyFunctions));
  }

  @Override
  public @NonNull Chain<T> withRetry(int maxAttempts, Duration delay) {
    this.maxAttempts = maxAttempts;
    this.retryDelay = delay;
    return this;
  }

  @Override
  public @NonNull Chain<T> withRetry(
      int maxAttempts, Duration delay, RetryPredicateCallback predicate) {
    this.maxAttempts = maxAttempts;
    this.retryDelay = delay;
    this.retryPredicate = predicate;
    return this;
  }

  @Override
  public @NonNull Chain<T> withTimeout(Duration duration) {
    this.timeout = duration;
    return this;
  }

  @Override
  public @NonNull Chain<T> onErrorFallback(SynchronousFallbackHandler<T, T> fallback) {
    this.synchronousFallbackHandler = (SynchronousFallbackHandler<Object, Object>) fallback;
    return this;
  }

  @Override
  public @NonNull Chain<T> onErrorFallbackAsync(AsynchronousFallbackHandler<T, T> fallbackHandler) {
    this.asynchronousFallbackHandler =
        (AsynchronousFallbackHandler<Object, Object>) fallbackHandler;
    return this;
  }

  @Override
  public @NonNull Chain<T> onErrorResume(Function<Throwable, T> fallbackValue) {
    this.resumeFallback = (Function<Throwable, Object>) fallbackValue;
    return this;
  }

  @Override
  public @NonNull Chain<T> onStart(StepStartCallback<Object> hook) {
    this.onStart = hook;
    return this;
  }

  @Override
  public @NonNull Chain<T> onSuccess(StepSuccessCallback<Object, Object> hook) {
    this.onSuccess = hook;
    return this;
  }

  @Override
  public @NonNull Chain<T> onFailure(StepFailureCallback<Object> hook) {
    this.onFailure = hook;
    return this;
  }

  @Override
  public @NonNull Chain<T> onRetry(StepRetryCallback hook) {
    this.onRetry = hook;
    return this;
  }

  @Override
  @Deprecated
  public @NonNull T blocking() {
    return blocking(null);
  }

  @Override
  public @NonNull T blocking(@Nullable Duration timeoutOverride) {
    Duration effectiveTimeout = timeoutOverride != null ? timeoutOverride : this.timeout;
    if (effectiveTimeout == null) {
      return (T) runBlockingWithRetry();
    }

    CompletableFuture<Object> future =
        runAsyncWithRetry(DEFAULT_EXECUTOR)
            .orTimeout(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
    try {
      return (T) future.join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) throw re;
      throw new CompletionExceptionWrapper(cause);
    }
  }

  private Object runBlockingWithRetry() {
    Exception lastError = null;
    Object lastInputBeforeFailure = null;
    int failedStepCount = 0;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        Object value = null;
        for (Step<Object, Object> step : steps) {
          failedStepCount = step.order();
          lastInputBeforeFailure = value;

          if (onStart != null) {
            onStart.callback(step.order(), value);
          }

          long startTime = System.nanoTime();
          try {
            value = step.executeBlocking(value);
          } catch (Exception stepFailure) {
            if (onFailure != null) {
              onFailure.callback(step.order(), lastInputBeforeFailure, stepFailure);
            }
            if (resumeFallback != null) {
              value = resumeFallback.apply(stepFailure);
              continue;
            }
            throw stepFailure;
          }

          long endTime = System.nanoTime();
          Duration stepDuration = Duration.ofNanos(endTime - startTime);

          if (onSuccess != null) {
            onSuccess.callback(step.order(), lastInputBeforeFailure, value, stepDuration);
          }
        }
        return value;
      } catch (Exception exception) {
        lastError = exception;
        boolean lastAttempt = attempt == maxAttempts;
        boolean predicateStops = retryPredicate != null && !retryPredicate.callback(exception);

        if (lastAttempt || predicateStops) {
          break;
        }
        if (onRetry != null) {
          onRetry.callback(failedStepCount, attempt, exception, retryDelay);
        }
        sleep(retryDelay);
      }
    }
    return resolveBlockingFallback(lastError, lastInputBeforeFailure);
  }

  private Object resolveBlockingFallback(Exception lastError, Object input) {
    Throwable cause =
        (lastError instanceof CompletionExceptionWrapper) ? lastError.getCause() : lastError;

    if (synchronousFallbackHandler != null) {
      try {
        return synchronousFallbackHandler.handle(cause, input);
      } catch (Exception e) {
        throw new CompletionExceptionWrapper(e);
      }
    }
    if (asynchronousFallbackHandler != null) {
      try {
        return asynchronousFallbackHandler.handle(cause, input).join();
      } catch (Exception e) {
        throw new CompletionExceptionWrapper(e);
      }
    }
    if (lastError instanceof RuntimeException re) {
      throw re;
    }
    throw new CompletionExceptionWrapper(lastError);
  }

  @Override
  public @NonNull CompletableFuture<T> asynchronously() {
    return asynchronously(DEFAULT_EXECUTOR);
  }

  @Override
  public @NonNull CompletableFuture<T> asynchronously(@NonNull Executor executor) {
    CompletableFuture<Object> result = runAsyncWithRetry(executor);
    if (timeout != null && !timeout.isZero()) {
      result = result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    return (CompletableFuture<T>) result;
  }

  private CompletableFuture<Object> runAsyncWithRetry(Executor executor) {
    return runAsyncAttempt(1, executor);
  }

  private CompletableFuture<Object> runAsyncAttempt(int attempt, Executor executor) {
    return runStepsAsync(executor)
        .handle(
            (value, throwable) -> {
              if (throwable == null) {
                return CompletableFuture.completedFuture(value);
              }

              Throwable cause =
                  (throwable instanceof CompletionException) ? throwable.getCause() : throwable;
              int failedStepCount = 0;
              Object lastInput = null;
              Throwable actualCause = cause;

              if (cause instanceof StepFailureContext sfc) {
                failedStepCount = sfc.stepOrder;
                lastInput = sfc.input;
                actualCause = sfc.getCause();
              }

              Exception actualException =
                  (actualCause instanceof Exception ex) ? ex : new RuntimeException(actualCause);

              boolean lastAttempt = attempt >= maxAttempts;
              boolean predicateStops =
                  retryPredicate != null && !retryPredicate.callback(actualException);

              if (lastAttempt || predicateStops) {
                return resolveAsyncFallback(actualException, lastInput, executor);
              }

              if (onRetry != null) {
                onRetry.callback(failedStepCount, attempt, actualException, retryDelay);
              }

              if (retryDelay != null && !retryDelay.isZero() && !retryDelay.isNegative()) {
                Executor delayedExecutor =
                    CompletableFuture.delayedExecutor(
                        retryDelay.toMillis(), TimeUnit.MILLISECONDS, executor);
                return CompletableFuture.supplyAsync(() -> null, delayedExecutor)
                    .thenCompose(ignored -> runAsyncAttempt(attempt + 1, executor));
              }

              return runAsyncAttempt(attempt + 1, executor);
            })
        .thenCompose(Function.identity());
  }

  private CompletableFuture<Object> runStepsAsync(Executor executor) {
    CompletableFuture<Object> result = CompletableFuture.completedFuture(null);

    for (Step<Object, Object> step : steps) {
      result =
          result.thenCompose(
              value -> {
                if (onStart != null) {
                  onStart.callback(step.order(), value);
                }

                long startTime = System.nanoTime();

                return step.executeAsync(value, executor)
                    .handle(
                        (next, stepFailure) -> {
                          long endTime = System.nanoTime();
                          Duration stepDuration = Duration.ofNanos(endTime - startTime);

                          if (stepFailure == null) {
                            if (onSuccess != null) {
                              onSuccess.callback(step.order(), value, next, stepDuration);
                            }
                            return CompletableFuture.completedFuture(next);
                          }

                          Throwable cause =
                              (stepFailure instanceof CompletionException)
                                  ? stepFailure.getCause()
                                  : stepFailure;
                          Exception exception =
                              (cause instanceof Exception ex) ? ex : new RuntimeException(cause);

                          if (onFailure != null) {
                            onFailure.callback(step.order(), value, exception);
                          }

                          if (resumeFallback != null) {
                            try {
                              Object fallbackValue = resumeFallback.apply(exception);
                              return CompletableFuture.completedFuture(fallbackValue);
                            } catch (Exception e) {
                              return CompletableFuture.failedFuture(e);
                            }
                          }

                          return CompletableFuture.failedFuture(
                              new StepFailureContext(step.order(), value, exception));
                        })
                    .thenCompose(Function.identity());
              });
    }
    return result;
  }

  private CompletableFuture<Object> resolveAsyncFallback(
      Throwable cause, Object lastInput, Executor executor) {
    if (asynchronousFallbackHandler != null) {
      try {
        return asynchronousFallbackHandler.handle(cause, lastInput);
      } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
      }
    }
    if (synchronousFallbackHandler != null) {
      return CompletableFuture.supplyAsync(
          () -> {
            try {
              return synchronousFallbackHandler.handle(cause, lastInput);
            } catch (Exception e) {
              throw new CompletionException(e);
            }
          },
          executor);
    }
    return CompletableFuture.failedFuture(cause);
  }

  private static void sleep(Duration duration) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      return;
    }
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(interrupted);
    }
  }

  private int nextStepCount() {
    return steps.isEmpty() ? 0 : steps.getLast().order() + 1;
  }

  private static final class StepFailureContext extends RuntimeException {
    private final int stepOrder;
    private final Object input;

    StepFailureContext(int stepOrder, Object input, Throwable cause) {
      super(cause);
      this.stepOrder = stepOrder;
      this.input = input;
    }
  }

  private static final class CompletionExceptionWrapper extends RuntimeException {
    CompletionExceptionWrapper(Throwable cause) {
      super(cause);
    }
  }
}
