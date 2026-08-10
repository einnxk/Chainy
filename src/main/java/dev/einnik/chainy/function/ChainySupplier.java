package dev.einnik.chainy.function;

/**
 * A wrapped form of the {@link java.util.function.Supplier} functional interface, which
 * allows checked interfaces.
 *
 * @param <T> the type of the parameter
 * @author EinNik
 * @since 1.0.0
 */
@FunctionalInterface
public interface ChainySupplier<T> {
    T get() throws Exception;
}