package dev.einnik.chainy.callback;

import org.jspecify.annotations.NonNull;

import java.time.Duration;

/**
 * The {@link Callback} interface that allows the api consumer
 * to listen when a new step has been completed successfully.
 *
 * @param <T> the input that we invoke
 * @param <R> and the result the step produces
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface StepSuccessCallback<T, R> extends Callback {
    void callback(int stepCount, @NonNull T input, @NonNull R result, Duration duration);
}