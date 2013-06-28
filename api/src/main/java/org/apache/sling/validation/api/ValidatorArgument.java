package org.apache.sling.validation.api;

/**
 * A {@code ValidatorArgument} provides specific constraints for a {@link Validator}.
 */
public interface ValidatorArgument {

    /**
     * Returns the argument's name.
     *
     * @return the name
     */
    String getName();

    /**
     * Returns the argument's value
     *
     * @return the value
     */
    String getValue();
}
