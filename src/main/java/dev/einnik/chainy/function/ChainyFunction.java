package dev.einnik.chainy.function;

/**
 * A wrapped form of the {@link java.util.function.Function} functional interface, which
 * allows checked interfaces.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface ChainyFunction<T, R> {
    R apply(T input) throws Exception;
}