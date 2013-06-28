package org.apache.sling.validation.impl;

import org.apache.sling.validation.api.Field;
import org.apache.sling.validation.api.ValidationModel;

import java.util.Set;

public class ValidationModelImpl implements ValidationModel {

    private Set<Field> fields;
    private String validatedResourceType;
    private String[] applicablePaths;

    public ValidationModelImpl(Set<Field> fields, String validatedResourceType, String[] applicablePaths) {
        this.fields = fields;
        this.validatedResourceType = validatedResourceType;
        this.applicablePaths = applicablePaths;
    }

    public Set<Field> getFields() {
        return fields;
    }

    public String getValidatedResourceType() {
        return validatedResourceType;
    }

    public String[] getApplicablePaths() {
        return applicablePaths;
    }
}
