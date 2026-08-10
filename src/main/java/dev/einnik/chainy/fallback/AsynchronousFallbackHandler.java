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
package dev.einnik.chainy.fallback;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NonNull;

/**
 * The asynchronous fallback handler that is executer when a step completely fails and no retries
 * are available.
 *
 * @param <T> the type the step provides
 * @param <R> the type the step should return wrapped into an {@link CompletableFuture}
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface AsynchronousFallbackHandler<T, R> {
  @NonNull CompletableFuture<R> handle(@NonNull Throwable cause, @NonNull T input)
      throws CompletionException;
}
