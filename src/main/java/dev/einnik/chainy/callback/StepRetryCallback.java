package dev.einnik.chainy.callback;

import org.jspecify.annotations.NonNull;

import java.time.Duration;

/**
 * The {@link Callback} interface that allows the api consumer
 * to listen when a step is retried.
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface StepRetryCallback extends Callback {
    void callback(int stepCount, int attempt, @NonNull Throwable cause, @NonNull Duration duration);
}