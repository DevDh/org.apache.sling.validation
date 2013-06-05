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

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.validation.api.Field;
import org.apache.sling.validation.api.Type;
import org.apache.sling.validation.api.ValidationModel;
import org.apache.sling.validation.api.ValidationService;
import org.apache.sling.validation.api.Validator;
import org.apache.sling.validation.api.ValidatorArgument;
import org.apache.sling.validation.api.ValidatorLookupService;
import org.apache.sling.validation.impl.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component()
@Service(ValidationService.class)
public class ValidationServiceImpl implements ValidationService {

    static final Logger LOG = LoggerFactory.getLogger(ValidationServiceImpl.class);
    static final String VALIDATED_RESOURCE_TYPE = "validatedResourceType";
    static final String APPLICABLE_PATHS = "applicablePaths";
    static final String MODEL_XPATH_QUERY = "/jcr:root//*[@sling:resourceType=\"%s\" and @%s=\"%s\"]";
    static final String VALIDATION_MODEL_RESOURCE_TYPE = "sling/validation/model";
    static final String FIELDS = "fields";
    static final String FIELD_TYPE = "fieldType";
    static final String VALIDATORS = "validators";
    static final String VALIDATOR_ARGUMENTS = "validatorArguments";
    private Map<String, Trie<ValidationModel>> validationModelsCache = new ConcurrentHashMap<String, Trie<ValidationModel>>();

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private ValidatorLookupService validatorLookupService;

    public ValidationModel getValidationModel(String validatedResourceType, String resourcePath) {
        ValidationModel model = null;
        Trie<ValidationModel> modelsForResourceType = validationModelsCache.get(validatedResourceType);
        if (modelsForResourceType != null) {
            model = modelsForResourceType.getElementForLongestMatchingKey(resourcePath).getValue();
        }
        if (model == null) {
            modelsForResourceType = searchAndStoreValidationModel(validatedResourceType);
            if (modelsForResourceType != null) {
                model = modelsForResourceType.getElementForLongestMatchingKey(resourcePath).getValue();
            }
        }
        return model;
    }

    /**
     * Searches for valid validation models in the JCR repository for a certain resource type. All validation models will be returned in a
     * {@link Trie} data structure for easy retrieval of the models using their {@code applicable paths} as trie keys.
     *
     * @param validatedResourceType the type of resource for which to scan the JCR repository for validation models
     * @return a {@link Trie} with the validation models; an empty trie if no model is found
     */
    private Trie<ValidationModel> searchAndStoreValidationModel(String validatedResourceType) {
        Trie<ValidationModel> modelsForResourceType = null;
        ResourceResolver rr = null;
        ValidationModel vm = null;
        try {
            rr = rrf.getAdministrativeResourceResolver(null);
            final String queryString = String.format(MODEL_XPATH_QUERY, VALIDATION_MODEL_RESOURCE_TYPE,
                    VALIDATED_RESOURCE_TYPE, validatedResourceType);
            Iterator<Resource> models = rr.findResources(queryString, Query.XPATH);
            while (models.hasNext()) {
                Resource model = models.next();
                ValueMap validationModelProperties = model.adaptTo(ValueMap.class);
                String[] applicablePaths = PropertiesUtil.toStringArray(validationModelProperties.get(APPLICABLE_PATHS, String[].class));
                Set<Field> fields = new HashSet<Field>();
                if (StringUtils.isNotEmpty(validatedResourceType)) {
                    Resource r = model.getChild(FIELDS);
                    if (r != null) {
                        Iterator<Resource> fieldsIterator = r.listChildren();
                        while (fieldsIterator.hasNext()) {
                            Resource field = fieldsIterator.next();
                            ValueMap fieldProperties = field.adaptTo(ValueMap.class);
                            String fieldName = field.getName();
                            Type type = Type.getType(fieldProperties.get(FIELD_TYPE, String.class));
                            Resource validators = field.getChild(VALIDATORS);
                            Map<Validator, List<ValidatorArgument>> validatorsMap = new HashMap<Validator, List<ValidatorArgument>>();
                            if (validators != null) {
                                Iterator<Resource> validatorsIterator = validators.listChildren();
                                while (validatorsIterator.hasNext()) {
                                    Resource validator = validatorsIterator.next();
                                    ValueMap validatorProperties = validator.adaptTo(ValueMap.class);
                                    String validatorName = validator.getName();
                                    Validator v = validatorLookupService.getValidator(validatorName);
                                    String[] validatorArguments = validatorProperties.get(VALIDATOR_ARGUMENTS, String[].class);
                                    List<ValidatorArgument> validatorArgumentsList = new ArrayList<ValidatorArgument>();
                                    if (validatorArguments != null) {
                                        for (String arg : validatorArguments) {
                                            String[] keyValuePair = arg.split("=");
                                            if (keyValuePair.length != 2) {
                                                continue;
                                            }
                                            ValidatorArgument va = new ValidatorArgumentImpl(keyValuePair[0], keyValuePair[1]);
                                            validatorArgumentsList.add(va);
                                        }
                                    }
                                    validatorsMap.put(v, validatorArgumentsList);
                                }
                            }
                            Field f = new FieldImpl(fieldName, type, validatorsMap);
                            fields.add(f);
                        }
                        if (!fields.isEmpty()) {
                            vm = new ValidationModelImpl(fields, validatedResourceType, applicablePaths);
                            for (String applicablePath : vm.getApplicablePaths()) {
                                modelsForResourceType = validationModelsCache.get(validatedResourceType);
                                if (modelsForResourceType == null) {
                                    modelsForResourceType = new Trie<ValidationModel>();
                                    validationModelsCache.put(validatedResourceType, modelsForResourceType);
                                }
                                modelsForResourceType.insert(applicablePath, vm);
                            }
                        }
                    }
                }
            }
        } catch (LoginException e) {
            LOG.error("Unable to obtain a resource resolver.", e);
        }
        if (rr != null) {
            rr.close();
        }
        return modelsForResourceType;
    }
}
