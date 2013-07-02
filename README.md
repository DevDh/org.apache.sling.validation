# Apache Sling Validation Framework Prototype
This prototype proposes a new validation framework API for Apache Sling and also provides a default implementation, together with a testing
services bundle.

The core service of the validation framework is represented by the `ValidationService` interface, which provides the following methods:
```java
/**
 * Tries to locate a {@link ValidationModel} that is able to validate a {@code Resource} of type {@code validatedResourceType}.
 *
 * @param validatedResourceType the type of {@code Resources} the model validates
 * @param applicablePath        the model's applicable path (the path of the validated resource)
 * @return a {@code ValidationModel} if one is found, {@code null} otherwise
 */
ValidationModel getValidationModel(String validatedResourceType, String applicablePath);

/**
 * Validates a {@link ValueMap} using a specific {@link ValidationModel}.
 *
 * @param valueMap the map to validate
 * @param model    the model with which to perform the validation
 * @return a {@link ValidationResult} that provides the necessary information
 * @throws NullPointerException if either one of the two parameters are null
 */
ValidationResult validate(ValueMap valueMap, ValidationModel model) throws NullPointerException;
```

Using value maps in the `validate` method provides a very flexible validation mechanism. The default implementation allows `Resources`'
validation (by adapting them to `ValueMap`) or `SlingHttpServletRequest`s - an `AdapterFactory` is provided for adapting a request to a
`ValueMap` by extracting the request's parameters.

The default implementation also provides a content-driven `ValidationModel` creation from JCR content-tree structures of the following form:
<pre>
validationModel
    @validatedResourceType
    @applicablePaths = [path1,path2,...] (optional)
    @sling:resourceType = sling/validation/model
    fields
        field1
            @fieldType
            validators
                validator1
                    @validatorArguments = [key=value,key=value...] (optional)
                validatorN
                    @validatorArguments = [key=value,key=value...] (optional)
        fieldN
            @fieldType
            validators
                validator1
                @validatorArguments = [key=value,key=value...] (optional)
</pre>
where all nodes can be of type `nt:unstructured`.

## Testing the default implementation
1. Clone this repository and install the bundles (api, core test-services, it-http)

        git clone https://github.com/raducotescu/org.apache.sling.validation.git
        cd org.apache.sling.validation/
        mvn clean install

    This will install all the artifacts in your local repository. During the install phase of the `it-http` module a Sling Launchpad instance
    will be automatically turned on and all the tests from the `it-http` module will be run.
