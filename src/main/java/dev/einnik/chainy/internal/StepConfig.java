package dev.einnik.chainy.internal;

import dev.einnik.chainy.callback.RetryPredicateCallback;
import dev.einnik.chainy.fallback.SynchronousFallbackHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;

@Getter
@Setter
@Accessors(fluent = true)
@RequiredArgsConstructor
public class StepConfig<T, R> {

    private final int stepCount;
    private int maxAttempts = 1;
    private Duration delay = Duration.ZERO;
    private RetryPredicateCallback retryPredicate = _ -> true;
    private Duration timeout;
    private SynchronousFallbackHandler<T, R> fallbackHandler;
}