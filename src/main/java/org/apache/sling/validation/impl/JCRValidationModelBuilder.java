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
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
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
import org.apache.sling.validation.api.Validator;
import org.apache.sling.validation.api.ValidatorArgument;
import org.apache.sling.validation.api.ValidatorLookupService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p> The {@code JCRValidationModelBuilder} is responsible for monitoring the JCR repository in search of new {@link
 * org.apache.sling.validation.api.ValidationModel}s defined as valid {@code sling/validation/model} resources. </p> <p> A valid
 * content-tree {@code ValidationModel} has the following structure:
 * <pre>
 *         validationModel
 *              &#064;validatedResourceType
 *              &#064;applicablePaths = [path1,path2,...] (optional)
 *              &#064;sling:resourceType = sling/validation/model
 *              fields
 *                  field1
 *                      &#064;fieldType
 *                      validators
 *                          validator1
 *                              &#064;validatorArguments = [key=value,key=value...] (optional)
 *                          validatorN
 *                              &#064;validatorArguments = [key=value,key=value...] (optional)
 *                  fieldN
 *                      validators
 *                          validator1
 *                              &#064;validatorArguments = [key=value,key=value...] (optional)
 * </pre>
 * </p>
 */
@Component()
@Properties({
        @Property(name = EventConstants.EVENT_TOPIC, value = {SlingConstants.TOPIC_RESOURCE_ADDED, SlingConstants.TOPIC_RESOURCE_CHANGED,
                SlingConstants.TOPIC_RESOURCE_REMOVED}),
        @Property(name = EventConstants.EVENT_FILTER, value = "(&(path=/apps/*|/libs/*)(resourceType=sling/validation/model))")
})
public class JCRValidationModelBuilder implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JCRValidationModelBuilder.class);

    @Reference
    private ThreadPoolManager threadPoolManager;

    @Reference
    private ResourceResolverFactory rrf;

    @Reference

    private ValidatorLookupService vls;
    private ThreadPool threadPool;
    private BundleContext bundleContext;
    private Map<String, ServiceRegistration> validationModels = new ConcurrentHashMap<String, ServiceRegistration>();

    public void handleEvent(Event event) {
        threadPool.execute(new ModelBuilder(event));
    }

    // OSGI ################################################################################################################################
    protected void activate(ComponentContext componentContext) {
        threadPool = threadPoolManager.get("JCRValidationModelBuilder");
        bundleContext = componentContext.getBundleContext();
    }

    protected void deactivate(ComponentContext componentContext) {
        if (threadPool != null) {
            threadPoolManager.release(threadPool);
        }
        this.bundleContext = null;
    }

    private class ModelBuilder implements Runnable {

        static final String FIELDS = "fields";
        static final String FIELD_TYPE = "fieldType";
        static final String VALIDATORS = "validators";
        static final String VALIDATOR_NAME = "validatorName";
        static final String VALIDATOR_ARGUMENTS = "validatorArguments";
        private Event event;

        public ModelBuilder(Event event) {
            this.event = event;
        }


        public void run() {
            final String topic = event.getTopic();
            final String rootPath = (String) event.getProperty("path");
            if (SlingConstants.TOPIC_RESOURCE_ADDED.equals(topic) || SlingConstants.TOPIC_RESOURCE_CHANGED.equals(topic)) {
                if (StringUtils.isNotEmpty(rootPath)) {
                    ValidationModel vm = buildValidationModel(rootPath);
                    if (vm != null) {
                        final Dictionary<String, Object> serviceProps = new Hashtable<String, Object>();
                        //serviceProps.put(ValidationModel.SCR_PROP_NAME_RESOURCE_TYPE, vm.getValidatedResourceType());
                        //serviceProps.put(ValidationModel.SCR_PROP_NAME_APPLICABLE_PATHS, vm.getApplicablePaths());
                        ServiceRegistration serviceRegistration = bundleContext.registerService(ValidationModel.class.getName(), vm, serviceProps);
                        validationModels.put(rootPath, serviceRegistration);
                    }
                } else {
                    LOG.error("Empty path for {} event", topic);
                }
            } else if (SlingConstants.TOPIC_RESOURCE_REMOVED.equals(topic)) {
                ServiceRegistration serviceRegistration = validationModels.get(rootPath);
                if (serviceRegistration != null) {
                    serviceRegistration.unregister();
                    validationModels.remove(rootPath);
                }
            }
        }

        public ValidationModel buildValidationModel(String rootPath) {
            ValidationModel vm = null;
            try {
                ResourceResolver rr = rrf.getAdministrativeResourceResolver(null);
                Resource validationModelResource = rr.getResource(rootPath);
                ValueMap validationModelProperties = validationModelResource.adaptTo(ValueMap.class);
                /*String validatedResourceType = validationModelProperties.get(ValidationModel.SCR_PROP_NAME_RESOURCE_TYPE, String.class);
                String[] applicablePath = PropertiesUtil.toStringArray(validationModelProperties.get(ValidationModel
                        .SCR_PROP_NAME_APPLICABLE_PATHS, String[].class));
                Set<Field> fields = new HashSet<Field>();
                if (StringUtils.isNotEmpty(validatedResourceType)) {
                    Resource r = validationModelResource.getChild(FIELDS);
                    if (r != null) {
                        Iterator<Resource> fieldsIterator = r.listChildren();
                        while (fieldsIterator.hasNext()) {
                            Resource field = fieldsIterator.next();
                            ValueMap fieldProperties = field.adaptTo(ValueMap.class);
                            String fieldName = field.getName();
                            Type type = null;
                            try {
                                type = Type.valueOf(fieldProperties.get(FIELD_TYPE, String.class));
                            } catch (IllegalArgumentException e) {
                                LOG.error("Illegal value for fieldType property on resource " + field.getPath(), e);
                                continue;
                            }
                            Resource validators = field.getChild(VALIDATORS);
                            Map<Validator, List<ValidatorArgument>> validatorsMap = new HashMap<Validator, List<ValidatorArgument>>();
                            if (validators != null) {
                                Iterator<Resource> validatorsIterator = validators.listChildren();
                                while (validatorsIterator.hasNext()) {
                                    Resource validator = validatorsIterator.next();
                                    ValueMap validatorProperties = validator.adaptTo(ValueMap.class);
                                    String validatorName = validatorProperties.get(VALIDATOR_NAME, String.class);
                                    Validator v = vls.getValidator(validatorName);
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
                            vm = new ValidationModelImpl(fields, validatedResourceType, applicablePath);
                        }
                    }
                }*/

            } catch (LoginException e) {
                LOG.error("Unable to build validation model", e);
            }
            return vm;
        }
    }
}
