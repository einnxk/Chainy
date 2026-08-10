package dev.einnik.chainy.callback;

import org.jspecify.annotations.NonNull;

/**
 * The {@link Callback} interface that allows the api consumer
 * to listen when a new step is invoked not finished.
 *
 * @param <T> the input that we invoke
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface StepStartCallback<T> extends Callback {
    void callback(int stepCount, @NonNull T input);
}