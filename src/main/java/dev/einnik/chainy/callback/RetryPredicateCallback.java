package dev.einnik.chainy.callback;

import org.jspecify.annotations.NonNull;

/**
 * A {@link Callback} that decides if a {@link Exception} triggers
 * a new try.
 *
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface RetryPredicateCallback extends Callback {
    void callback(@NonNull Throwable cause);
}