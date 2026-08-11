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
package dev.einnik.chainy.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.einnik.chainy.Chain;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class SimpleChainTest {

  @Test
  void testHappyPathBlocking() {
    String result =
        Chain.from("Hello")
            .then(1, input -> input + " World")
            .then(2, input -> input + "!")
            .blocking();

    assertThat(result).isEqualTo("Hello World!");
  }

  @Test
  void testHappyPathAsync() throws Exception {
    CompletableFuture<String> future =
        Chain.startWith(() -> "Async")
            .thenAsynchronously(1, input -> CompletableFuture.completedFuture(input + " Process"))
            .then(2, String::toUpperCase)
            .asynchronously();

    assertThat(future.get()).isEqualTo("ASYNC PROCESS");
  }

  @Test
  void testRetryMechanismSuccessOnThirdAttempt() {
    AtomicInteger attempts = new AtomicInteger(0);
    AtomicInteger retryCallbackCounter = new AtomicInteger(0);

    String result =
        Chain.from("Input")
            .then(
                1,
                input -> {
                  if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("Temporary failure #" + attempts.get());
                  }
                  return input + " -> Success";
                })
            .withRetry(3, Duration.ofMillis(10))
            .onRetry((stepOrder, attempt, cause, delay) -> retryCallbackCounter.incrementAndGet())
            .blocking();

    assertThat(result).isEqualTo("Input -> Success");
    assertThat(attempts.get()).isEqualTo(3);
    assertThat(retryCallbackCounter.get()).isEqualTo(2);
  }

  @Test
  void testOnErrorResume() {
    List<Integer> executedSteps = new ArrayList<>();

    String result =
        Chain.from("Start")
            .then(
                1,
                input -> {
                  executedSteps.add(1);
                  throw new IllegalArgumentException("Bad argument");
                })
            .then(
                2,
                input -> {
                  executedSteps.add(2);
                  return input + " -> Final";
                })
            .onErrorResume(throwable -> "FallbackValue")
            .blocking();

    assertThat(result).isEqualTo("FallbackValue -> Final");
    assertThat(executedSteps).containsExactly(1, 2);
  }

  @Test
  void testTerminalFallbackAfterFailedRetries() {
    AtomicInteger retryCounter = new AtomicInteger(0);

    String result =
        (String)
            Chain.from("Data")
                .then(
                    1,
                    input -> {
                      throw new RuntimeException("Unrecoverable error");
                    })
                .withRetry(2, Duration.ofMillis(5))
                .onRetry((stepOrder, attempt, cause, delay) -> retryCounter.incrementAndGet())
                .onErrorFallback((cause, input) -> "SafeFallbackFor: " + input)
                .blocking();

    assertThat(result).isEqualTo("SafeFallbackFor: Data");
    assertThat(retryCounter.get()).isEqualTo(1);
  }

  @Test
  void testAsyncTimeout() {
    CompletableFuture<String> future =
        Chain.startWith(() -> "Start")
            .thenAsynchronously(
                1,
                input ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            Thread.sleep(200);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                          return input + " Slow";
                        }))
            .withTimeout(Duration.ofMillis(50))
            .asynchronously();

    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
  }

  @Test
  void testLifecycleCallbacks() {
    List<String> events = new ArrayList<>();

    Chain.from("Init")
        .then(1, input -> input + " -> Step1")
        .onStart((stepOrder, input) -> events.add("START_" + stepOrder + ":" + input))
        .onSuccess(
            (stepOrder, input, result, duration) -> {
              events.add("SUCCESS_" + stepOrder + ":" + result);
              assertThat(duration).isNotNull();
            })
        .blocking(null);

    assertThat(events)
        .containsExactly(
            "START_0:null", "SUCCESS_0:Init", "START_1:Init", "SUCCESS_1:Init -> Step1");
  }
}
