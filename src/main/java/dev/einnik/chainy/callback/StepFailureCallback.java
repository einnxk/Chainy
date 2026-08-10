package dev.einnik.chainy.callback;

import org.jspecify.annotations.NonNull;

/**
 * The {@link Callback} interface that allows the api consumer
 * to listen when a step execution fails and throws an {@link Exception}.
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface StepFailureCallback extends Callback {
    void callback(int stepCount, @NonNull Exception exception);
}