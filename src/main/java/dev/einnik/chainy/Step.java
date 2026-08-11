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

import dev.einnik.chainy.function.AsyncChainyFunction;
import dev.einnik.chainy.function.ChainyConsumer;
import dev.einnik.chainy.function.ChainyFunction;
import dev.einnik.chainy.function.ChainySupplier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;

/**
 * A single execution unit inside a chain.
 *
 * <p>This is the piece that makes {@code then(...)} and {@code thenAsynchronously(...)} produce an
 * identical builder: a step is defined once, independent of how it will later run. {@link
 * #executeBlocking} and {@link #executeAsync} are two different ways to run the very same step —
 * which one is called is decided by the chain's terminal operation ({@code blocking()} / {@code
 * asynchronously()}), never by the step itself.
 *
 * @param <IN> the input type consumed by this step
 * @param <OUT> the output type produced by this step
 * @author EinNik
 * @since 1.0.0
 * @see Chain
 */
public interface Step<IN, OUT> {

  /**
   * @return the execution order this step was registered with.
   */
  int order();

  /**
   * Runs this step on the calling thread, blocking until a result is available.
   *
   * @param input the input from the step
   * @throws Exception when an error or time out occurs
   * @return the result that is handed to the next task
   */
  @NonNull OUT executeBlocking(@NonNull IN input) throws Exception;

  /**
   * Runs this step asynchronously, using {@code executor} if the step is not already async.
   *
   * @param input the input from the step
   * @param executor the executor that provides the thread for the future
   * @throws CompletionException when an error in the future execution occurs
   * @return the result that is handed to the next task wrapped into an {@link CompletableFuture}
   */
  @NonNull CompletableFuture<OUT> executeAsync(@NonNull IN input, @NonNull Executor executor)
      throws CompletionException;

  /**
   * Wraps a synchronous function as a step.
   *
   * @param order the order of the step
   * @param function the function that is executed when the step is fired
   * @param <IN> the input type consumed by the step
   * @param <OUT> the output type produced by the step
   * @return the function wrapped into a step
   */
  static <IN, OUT> @NonNull Step<IN, OUT> ofSync(
      int order, @NonNull ChainyFunction<IN, OUT> function) {
    return new Step<>() {
      @Override
      public int order() {
        return order;
      }

      @Override
      public @NonNull OUT executeBlocking(@NonNull IN input) throws Exception {
        return function.apply(input);
      }

      @Override
      public @NonNull CompletableFuture<OUT> executeAsync(
          @NonNull IN input, @NonNull Executor executor) throws CompletionException {
        return CompletableFuture.supplyAsync(
            () -> {
              try {
                return function.apply(input);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            executor);
      }
    };
  }

  /**
   * Wraps an asynchronous function as a step.
   *
   * @param order the order of the step
   * @param function the function that is executed async after the step is fired
   * @param <IN> the input type consumed by the step
   * @param <OUT> the output type produced by the step
   * @return the function wrapped into a step
   */
  static <IN, OUT> @NonNull Step<IN, OUT> ofAsync(
      int order, @NonNull AsyncChainyFunction<IN, OUT> function) {
    return new Step<>() {
      @Override
      public int order() {
        return order;
      }

      @Override
      public @NonNull OUT executeBlocking(@NonNull IN input) throws Exception {
        try {
          return function.apply(input).join();
        } catch (CompletionException e) {
          if (e.getCause() instanceof Exception ex) {
            throw new RuntimeException(ex);
          }

          throw e;
        }
      }

      @Override
      public @NonNull CompletableFuture<OUT> executeAsync(
          @NonNull IN input, @NonNull Executor executor) throws CompletionException {
        try {
          return function.apply(input);
        } catch (Exception e) {
          return CompletableFuture.failedFuture(e);
        }
      }
    };
  }

  /**
   * Wraps the initial synchronous supplier of a chain as a step that ignores its input.
   *
   * @param order the order of the execution - Should be fixed 0
   * @param supplier the start value supplier
   * @param <IN> the input type consumed by the step
   * @param <OUT> the output type produced by the step
   * @return the function wrapped into a step
   */
  static <IN, OUT> @NonNull Step<IN, OUT> ofStart(
      int order, @NonNull ChainySupplier<OUT> supplier) {
    return ofSync(order, _ -> supplier.get());
  }

  /**
   * Wraps the initial asynchronous supplier of a chain as a step that ignores its input.
   *
   * @param order the order of the execution - Should be fixed 0
   * @param supplier the start value supplier wrapped with an {@link CompletableFuture}
   * @param <IN> the input type consumed by the step
   * @param <OUT> the output type produced by the step
   * @return the function wrapped into a step
   */
  static <IN, OUT> @NonNull Step<IN, OUT> ofStartAsync(
      int order, @NonNull ChainySupplier<CompletableFuture<OUT>> supplier) {
    return ofAsync(
        order,
        _ -> {
          try {
            return supplier.get();
          } catch (CompletionException e) {
            return CompletableFuture.failedFuture(e);
          }
        });
  }

  /**
   * Wraps a peek consumer as a pass-through step.
   *
   * @param order the order of the execution
   * @param consumer the consumer with interacts with the latest return type
   * @param <T> the latest return type from the step before
   * @return the function wrapped into a step
   */
  static <T> @NonNull Step<T, T> ofPeek(int order, @NonNull ChainyConsumer<T> consumer) {
    return ofSync(
        order,
        value -> {
          consumer.accept(value);
          return value;
        });
  }

  /**
   * Wraps an if/else pair as a single conditional step.
   *
   * @param order the execution order
   * @param predicate the predicate condition for the if
   * @param ifFunction the function that is executed when the predicate is true
   * @param elseFunction the function that is executed when the predicate is false
   * @param <T> the latest return type from the step before
   * @return the function wrapped into a step
   */
  static <T> @NonNull Step<T, T> ofConditional(
      int order,
      @NonNull Predicate<T> predicate,
      @NonNull ChainyFunction<T, T> ifFunction,
      @NonNull ChainyFunction<T, T> elseFunction) {
    return ofSync(
        order,
        value -> predicate.test(value) ? ifFunction.apply(value) : elseFunction.apply(value));
  }

  /**
   * Wraps a fixed wait as a pass-through step.
   *
   * @param order the execution order
   * @param duration the duration the {@link Chain} is paused
   * @param <T> the latest return type from the step before
   * @return the delay wrapped into a step
   */
  static <T> @NonNull Step<T, T> ofDelay(int order, @NonNull Duration duration) {
    return new Step<>() {
      @Override
      public int order() {
        return order;
      }

      @Override
      public @NonNull T executeBlocking(@NonNull T input) throws Exception {
        Thread.sleep(duration.toMillis());
        return input;
      }

      @Override
      public @NonNull CompletableFuture<T> executeAsync(
          @NonNull T input, @NonNull Executor executor) throws CompletionException {
        Executor delayed =
            CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor);
        return CompletableFuture.supplyAsync(() -> input, delayed);
      }
    };
  }

  /**
   * Wraps a set of concurrent functions as a single step producing a joined list.
   *
   * @param order the execution order
   * @param functions a list of all the functions that are executed concurrent
   * @param <T> the latest return type from the step before
   * @param <R> the value we return to the next step
   * @return the function wrapped into a step
   */
  static <T, R> @NonNull Step<T, List<R>> ofAll(
      int order, @NonNull List<AsyncChainyFunction<T, R>> functions) {
    return new Step<>() {
      @Override
      public int order() {
        return order;
      }

      @Override
      public @NonNull List<R> executeBlocking(@NonNull T input) throws Exception {
        return executeAsync(input, Runnable::run).join();
      }

      @Override
      public @NonNull CompletableFuture<List<R>> executeAsync(
          @NonNull T input, @NonNull Executor executor) throws CompletionException {
        List<CompletableFuture<R>> futures =
            functions.stream()
                .map(
                    function -> {
                      try {
                        return function.apply(input);
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    })
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
      }
    };
  }
}
