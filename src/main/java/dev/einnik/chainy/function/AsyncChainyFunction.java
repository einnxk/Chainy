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
