package dev.einnik.chainy.function;

/**
 * A wrapped form of the {@link java.util.function.Consumer} functional interface, which
 * allows checked interfaces.
 *
 * @param <T> the type of the parameter
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface ChainyConsumer<T> {
    void accept(T input) throws Exception;
}