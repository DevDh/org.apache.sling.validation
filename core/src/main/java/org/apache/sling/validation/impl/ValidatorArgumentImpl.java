package org.apache.sling.validation.impl;

import org.apache.sling.validation.api.ValidatorArgument;

public class ValidatorArgumentImpl implements ValidatorArgument {

    private String name;
    private String value;

    public ValidatorArgumentImpl(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
