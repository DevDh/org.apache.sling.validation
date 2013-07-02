package org.apache.sling.validation.impl;

import org.apache.sling.validation.api.Field;
import org.apache.sling.validation.api.ValidationModel;

import java.util.Set;

public class JCRValidationModel implements ValidationModel {

    private Set<Field> fields;
    private String validatedResourceType;
    private String[] applicablePaths;
    private String jcrPath;

    public JCRValidationModel(String jcrPath, Set<Field> fields, String validatedResourceType, String[] applicablePaths) {
        this.jcrPath = jcrPath;
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

    public String getJcrPath() {
        return jcrPath;
    }
}
