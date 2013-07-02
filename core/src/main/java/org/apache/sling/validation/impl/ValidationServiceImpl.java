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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.apache.sling.validation.api.Field;
import org.apache.sling.validation.api.Type;
import org.apache.sling.validation.api.ValidationModel;
import org.apache.sling.validation.api.ValidationResult;
import org.apache.sling.validation.api.ValidationService;
import org.apache.sling.validation.api.Validator;
import org.apache.sling.validation.api.ValidatorLookupService;
import org.apache.sling.validation.api.exceptions.ValidatorException;
import org.apache.sling.validation.impl.util.Trie;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.query.Query;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component()
@Service(ValidationService.class)
public class ValidationServiceImpl implements ValidationService, EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ValidationServiceImpl.class);
    static final String VALIDATED_RESOURCE_TYPE = "validatedResourceType";
    static final String APPLICABLE_PATHS = "applicablePaths";
    static final String MODELS_HOME = "sling/validation/models/";
    static final String MODEL_XPATH_QUERY = "/jcr:root/%s/" + MODELS_HOME + "*[@sling:resourceType=\"%s\" and @%s=\"%s\"]";
    static final String VALIDATION_MODEL_RESOURCE_TYPE = "sling/validation/model";
    static final String FIELDS = "fields";
    static final String FIELD_TYPE = "fieldType";
    static final String VALIDATORS = "validators";
    static final String VALIDATOR_ARGUMENTS = "validatorArguments";
    static final String[] TOPICS = {SlingConstants.TOPIC_RESOURCE_REMOVED, SlingConstants.TOPIC_RESOURCE_CHANGED,
            SlingConstants.TOPIC_RESOURCE_ADDED};

    private Map<String, Trie<JCRValidationModel>> validationModelsCache = new ConcurrentHashMap<String, Trie<JCRValidationModel>>();
    private ThreadPool threadPool;
    private ServiceRegistration eventHandlerRegistration;

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private ValidatorLookupService validatorLookupService;

    @Reference
    private ThreadPoolManager tpm;

    @Override
    public ValidationModel getValidationModel(String validatedResourceType, String resourcePath) {
        ValidationModel model = null;
        Trie<JCRValidationModel> modelsForResourceType = validationModelsCache.get(validatedResourceType);
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

    @Override
    public ValidationResult validate(ValueMap valueMap, ValidationModel model) {
        ValidationResult result = new ValidationResultImpl("", true);
        if (valueMap == null || model == null) {
            throw new NullPointerException("ValidationResult.validate - cannot accept null parameters");
        }
        for (Field field : model.getFields()) {
            String fieldName = field.getName();
            Object fieldValues = valueMap.get(fieldName);
            if (fieldValues == null) {
                result = new ValidationResultImpl("Required field " + fieldName + " was not found.", false);
                break;
            }
            Type fieldType = field.getType();
            if (fieldValues instanceof String[]) {
                for (String fieldValue : (String[]) fieldValues) {
                    if (!fieldType.isValid(fieldValue)) {
                        result = new ValidationResultImpl("Field " + fieldName + " was expected to be of type " + fieldType.getName(), false);
                        break;
                    }
                    Map<Validator, Map<String, String>> validators = field.getValidators();
                    for (Map.Entry<Validator, Map<String, String>> validatorEntry : validators.entrySet()) {
                        Validator validator = validatorEntry.getKey();
                        Map<String, String> arguments = validatorEntry.getValue();
                        try {
                            if (!validator.validate(fieldValue, arguments)) {
                                result = new ValidationResultImpl("Field " + fieldName + " does not contain a valid value for the " + validator
                                        .getClass().getName() + " validator", false);
                                return result;
                            }
                        } catch (ValidatorException e) {
                            LOG.error("ValidatorException for field " + fieldName, e);
                            result = new ValidationResultImpl("", false);
                            return result;
                        }
                    }
                }
            } else if (fieldValues instanceof String) {
                String fieldValue = (String) fieldValues;
                if (!fieldType.isValid(fieldValue)) {
                    result = new ValidationResultImpl("Field " + fieldName + " was expected to be of type " + fieldType.getName(), false);
                    break;
                }
                Map<Validator, Map<String, String>> validators = field.getValidators();
                for (Map.Entry<Validator, Map<String, String>> validatorEntry : validators.entrySet()) {
                    Validator validator = validatorEntry.getKey();
                    Map<String, String> arguments = validatorEntry.getValue();
                    try {
                        if (!validator.validate(fieldValue, arguments)) {
                            result = new ValidationResultImpl("Field " + fieldName + " does not contain a valid value for the " + validator
                                    .getClass().getName() + " validator", false);
                            return result;
                        }
                    } catch (ValidatorException e) {
                        LOG.error("ValidatorException for field " + fieldName, e);
                        result = new ValidationResultImpl("", false);
                        return result;
                    }
                }
            }

        }
        return result;
    }

    @Override
    public void handleEvent(Event event) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                validationModelsCache.clear();
            }
        };
        threadPool.execute(task);
    }


    /**
     * Searches for valid validation models in the JCR repository for a certain resource type. All validation models will be returned in a
     * {@link Trie} data structure for easy retrieval of the models using their {@code applicable paths} as trie keys.
     * <p/>
     * A valid content-tree {@code ValidationModel} has the following structure:
     * <pre>
     * validationModel
     *      &#064;validatedResourceType
     *      &#064;applicablePaths = [path1,path2,...] (optional)
     *      &#064;sling:resourceType = sling/validation/model
     *      fields
     *          field1
     *              &#064;fieldType
     *              validators
     *                  validator1
     *                      &#064;validatorArguments = [key=value,key=value...] (optional)
     *                  validatorN
     *                      #064;validatorArguments = [key=value,key=value...] (optional)
     *          fieldN
     *              &#064;fieldType
     *              validators
     *                  validator1
     *                  &#064;validatorArguments = [key=value,key=value...] (optional)
     * </pre>
     *
     * @param validatedResourceType the type of resource for which to scan the JCR repository for validation models
     * @return a {@link Trie} with the validation models; an empty trie if no model is found
     */
    private Trie<JCRValidationModel> searchAndStoreValidationModel(String validatedResourceType) {
        Trie<JCRValidationModel> modelsForResourceType = null;
        ResourceResolver rr = null;
        JCRValidationModel vm;
        try {
            rr = rrf.getAdministrativeResourceResolver(null);
            String[] searchPaths = rr.getSearchPath();
            for (String searchPath : searchPaths) {
                if (searchPath.endsWith("/")) {
                    searchPath = searchPath.substring(0, searchPath.length() - 1);
                }
                final String queryString = String.format(MODEL_XPATH_QUERY, searchPath, VALIDATION_MODEL_RESOURCE_TYPE,
                        VALIDATED_RESOURCE_TYPE, validatedResourceType);
                Iterator<Resource> models = rr.findResources(queryString, Query.XPATH);
                while (models.hasNext()) {
                    Resource model = models.next();
                    String jcrPath = model.getPath();
                    ValueMap validationModelProperties = model.adaptTo(ValueMap.class);
                    String[] applicablePaths = PropertiesUtil.toStringArray(validationModelProperties.get(APPLICABLE_PATHS,
                            String[].class));
                    Set<Field> fields = new HashSet<Field>();
                    if (validatedResourceType != null && !"".equals(validatedResourceType)) {
                        Resource r = model.getChild(FIELDS);
                        if (r != null) {
                            Iterator<Resource> fieldsIterator = r.listChildren();
                            while (fieldsIterator.hasNext()) {
                                Resource field = fieldsIterator.next();
                                ValueMap fieldProperties = field.adaptTo(ValueMap.class);
                                String fieldName = field.getName();
                                Type type = Type.getType(fieldProperties.get(FIELD_TYPE, String.class));
                                Resource validators = field.getChild(VALIDATORS);
                                Map<Validator, Map<String, String>> validatorsMap = new HashMap<Validator, Map<String, String>>();
                                if (validators != null) {
                                    Iterator<Resource> validatorsIterator = validators.listChildren();
                                    while (validatorsIterator.hasNext()) {
                                        Resource validator = validatorsIterator.next();
                                        ValueMap validatorProperties = validator.adaptTo(ValueMap.class);
                                        String validatorName = validator.getName();
                                        Validator v = validatorLookupService.getValidator(validatorName);
                                        String[] validatorArguments = validatorProperties.get(VALIDATOR_ARGUMENTS, String[].class);
                                        Map<String, String> validatorArgumentsMap = new HashMap<String, String>();
                                        if (validatorArguments != null) {
                                            for (String arg : validatorArguments) {
                                                String[] keyValuePair = arg.split("=");
                                                if (keyValuePair.length != 2) {
                                                    continue;
                                                }
                                                validatorArgumentsMap.put(keyValuePair[0], keyValuePair[1]);
                                            }
                                        }
                                        validatorsMap.put(v, validatorArgumentsMap);
                                    }
                                }
                                Field f = new FieldImpl(fieldName, type, validatorsMap);
                                fields.add(f);
                            }
                            if (!fields.isEmpty()) {
                                vm = new JCRValidationModel(jcrPath, fields, validatedResourceType, applicablePaths);
                                modelsForResourceType = validationModelsCache.get(validatedResourceType);
                                /**
                                 * if the modelsForResourceType is null the canAcceptModel will return true: performance optimisation so that
                                 * the Trie is created only if the model is accepted
                                 */

                                if (canAcceptModel(vm, searchPath, searchPaths, modelsForResourceType)) {
                                    if (modelsForResourceType == null) {
                                        modelsForResourceType = new Trie<JCRValidationModel>();
                                        validationModelsCache.put(validatedResourceType, modelsForResourceType);
                                    }
                                    for (String applicablePath : vm.getApplicablePaths()) {
                                        modelsForResourceType.insert(applicablePath, vm);
                                    }
                                }
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

    /**
     * Checks if the {@code validationModel} does not override an existing stored model given the fact that the overlaying is done based on
     * the order in which the search paths are in the {@code searchPaths} array: the lower the index, the higher the priority.
     *
     * @param validationModel the model to be checked
     * @param currentSearchPath the current search path
     * @param searchPaths       the available search paths
     * @param validationModels  the existing validation models
     * @return {@code true} if the new model can be stored, {@code false} otherwise
     */
    private boolean canAcceptModel(JCRValidationModel validationModel, String currentSearchPath, String[] searchPaths,
                                   Trie<JCRValidationModel> validationModels) {
        // perform null check to optimise performance in callee - no need to previously create the Trie if we're not going to accept the model
        if (validationModels != null) {
            String relativeModelPath = validationModel.getJcrPath().replaceFirst(currentSearchPath, "");
            for (String searchPath : searchPaths) {
                if (!currentSearchPath.equals(searchPath)) {
                    for (String applicablePath : validationModel.getApplicablePaths()) {
                        JCRValidationModel existingVM = validationModels.getElement(applicablePath).getValue();
                        if (existingVM != null) {
                            String existingModelRelativeModelPath = existingVM.getJcrPath().replaceFirst(searchPath, "");
                            if (existingModelRelativeModelPath.equals(relativeModelPath)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    // OSGi ################################################################################################################################
    protected void activate(ComponentContext componentContext) {
        threadPool = tpm.get("Validation Service Thread Pool");
        ResourceResolver rr = null;
        try {
            rr = rrf.getAdministrativeResourceResolver(null);
        } catch (LoginException e) {
            LOG.error("Cannot obtain a resource resolver.");
        }
        if (rr != null) {
            StringBuilder sb = new StringBuilder("(");
            String[] searchPaths = rr.getSearchPath();
            if (searchPaths.length > 1) {
                sb.append("|");
            }
            for (String searchPath : searchPaths) {
                if (searchPath.endsWith("/")) {
                    searchPath = searchPath.substring(0, searchPath.length() - 1);
                }
                String path = searchPath + "/" + MODELS_HOME;
                sb.append("(path=" + path + "*)");
            }
            sb.append(")");
            Dictionary<String, Object> eventHandlerProperties = new Hashtable<String, Object>();
            eventHandlerProperties.put(EventConstants.EVENT_TOPIC, TOPICS);
            eventHandlerProperties.put(EventConstants.EVENT_FILTER, sb.toString());
            eventHandlerRegistration = componentContext.getBundleContext().registerService(EventHandler.class.getName(), this,
                    eventHandlerProperties);
            rr.close();
        } else {
            LOG.warn("Null resource resolver. Cannot apply path filtering for event processing. Skipping registering this service as an " +
                    "EventHandler");
        }
    }

    protected void deactivate(ComponentContext componentContext) {
        if (threadPool != null) {
            tpm.release(threadPool);
        }
        if (eventHandlerRegistration != null) {
            eventHandlerRegistration.unregister();
            eventHandlerRegistration = null;
        }
    }
}
