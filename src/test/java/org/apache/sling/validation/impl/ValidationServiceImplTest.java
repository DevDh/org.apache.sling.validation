/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.validation.impl;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.validation.api.Type;
import org.apache.sling.validation.api.ValidationModel;
import org.apache.sling.validation.api.ValidationService;
import org.apache.sling.validation.api.ValidatorLookupService;
import org.apache.sling.validation.impl.setup.MockedResourceResolver;
import org.apache.sling.validation.impl.validators.AlphaCharactersValidator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValidationServiceImplTest {

    /**
     * Assume the validation models are stored under (/libs|/apps) + / + VALIDATION_MODELS_RELATIVE_PATH.
     */
    private static final String VALIDATION_MODELS_RELATIVE_PATH = "sling/validation/models";
    private static final String APPS = "/apps";
    private static final String LIBS = "/libs";
    private static ResourceResolverFactory rrf;
    private static Resource appsValidatorsRoot;
    private static Resource libsValidatorsRoot;
    private ValidationService validationService;
    private ValidatorLookupService validatorLookupService;

    @BeforeClass
    public static void init() throws Exception {
        rrf = mock(ResourceResolverFactory.class);
        when(rrf.getAdministrativeResourceResolver(null)).thenAnswer(new Answer<ResourceResolver>() {
            public ResourceResolver answer(InvocationOnMock invocation) throws Throwable {
                return new MockedResourceResolver();
            }
        });
        ResourceResolver rr = rrf.getAdministrativeResourceResolver(null);
        if (rr != null) {
            appsValidatorsRoot = ResourceUtil.getOrCreateResource(rr, APPS + "/" + VALIDATION_MODELS_RELATIVE_PATH, (Map) null,
                    "sling:Folder", true);
            libsValidatorsRoot = ResourceUtil.getOrCreateResource(rr, LIBS + "/" + VALIDATION_MODELS_RELATIVE_PATH, (Map) null,
                    "sling:Folder", true);
            rr.close();
        }
    }

    @AfterClass
    public static void beNiceAndClean() throws Exception {
        ResourceResolver rr = rrf.getAdministrativeResourceResolver(null);
        if (rr != null) {
            if (appsValidatorsRoot != null) {
                rr.delete(appsValidatorsRoot);
            }
            if (libsValidatorsRoot != null) {
                rr.delete(libsValidatorsRoot);
            }
            rr.commit();
            rr.close();
        }
    }

    @Before
    public void setUp() {
        validationService = new ValidationServiceImpl();
        Whitebox.setInternalState(validationService, "rrf", rrf);
        validatorLookupService = mock(ValidatorLookupService.class);
    }

    @Test
    public void testGetValidationModel() throws Exception {
        when(validatorLookupService.getValidator("alphacharacters")).thenReturn(new AlphaCharactersValidator());
        Whitebox.setInternalState(validationService, "validatorLookupService", validatorLookupService);

        List<TestField> fields = new ArrayList<TestField>();
        TestField field = new TestField();
        field.name = "field1";
        field.type = Type.STRING;
        field.validators.put("alphacharacters", null);
        fields.add(field);
        ResourceResolver rr = rrf.getAdministrativeResourceResolver(null);
        if (rr != null) {
            createValidationModelResource(rr, libsValidatorsRoot.getPath(), "testValidationModel1", "sling/validation/test",
                    new String[]{"/apps/validation"}, fields);
            createValidationModelResource(rr, libsValidatorsRoot.getPath(), "testValidationModel2", "sling/validation/test",
                    new String[]{"/apps/validation/1",
                    "/apps/validation/2"}, fields);
            rr.close();
        }

        // BEST MATCHING PATH = /apps/validation/1; assume the applicable paths contain /apps/validation/2
        ValidationModel vm = validationService.getValidationModel("sling/validation/test", "/apps/validation/1/resource");
        assertTrue(arrayContainsString(vm.getApplicablePaths(), "/apps/validation/2"));

        // BEST MATCHING PATH = /apps/validation; assume the applicable paths contain /apps/validation but not /apps/validation/1
        vm = validationService.getValidationModel("sling/validation/test", "/apps/validation/resource");
        assertTrue(arrayContainsString(vm.getApplicablePaths(), "/apps/validation"));
        assertTrue(!arrayContainsString(vm.getApplicablePaths(), "/apps/validation/1"));
    }

    private void createValidationModelResource(ResourceResolver rr, String root, String name, String validatedResourceType,
                                               String[] applicableResourcePaths, List<TestField> fields) throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ValidationServiceImpl.VALIDATED_RESOURCE_TYPE, validatedResourceType);
        properties.put(ValidationServiceImpl.APPLICABLE_PATHS, applicableResourcePaths);
        properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, ValidationServiceImpl.VALIDATION_MODEL_RESOURCE_TYPE);
        properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
        Resource model = ResourceUtil.getOrCreateResource(rr, root + "/" + name, properties, JcrResourceConstants.NT_SLING_FOLDER, true);
        if (model != null) {
            Resource fieldsResource = ResourceUtil.getOrCreateResource(rr, model.getPath() + "/" + ValidationServiceImpl
                    .FIELDS, JcrConstants.NT_UNSTRUCTURED, null, true);
            if (fieldsResource != null) {
                for (TestField field : fields) {
                    Map<String, Object> fieldProperties = new HashMap<String, Object>();
                    fieldProperties.put(ValidationServiceImpl.FIELD_TYPE, field.type.getName());
                    fieldProperties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
                    Resource fieldResource = ResourceUtil.getOrCreateResource(rr, fieldsResource.getPath() + "/" + field.name,
                            fieldProperties, null, true);
                    if (fieldResource != null) {
                        Resource validators = ResourceUtil.getOrCreateResource(rr,
                                fieldResource.getPath() + "/" + ValidationServiceImpl.VALIDATORS,
                                JcrConstants.NT_UNSTRUCTURED, null, true);
                        if (validators != null) {
                            for (Map.Entry<String, String> v : field.validators.entrySet()) {
                                Map<String, Object> validatorProperties = new HashMap<String, Object>();
                                validatorProperties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
                                if (v.getValue() != null) {
                                    validatorProperties.put(ValidationServiceImpl.VALIDATOR_ARGUMENTS, v.getValue());
                                }
                                ResourceUtil.getOrCreateResource(rr, validators.getPath() + "/" + v.getKey(), validatorProperties, null,
                                        true);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean arrayContainsString(String[] array, String string) {
        boolean result = false;
        if (array != null && string != null) {
            for (String s : array) {
                if (string.equals(s)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private class TestField {
        String name;
        Type type;
        Map<String, String> validators;

        TestField() {
            validators = new HashMap<String, String>();
        }
    }

}
