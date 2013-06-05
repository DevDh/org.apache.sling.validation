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
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.validation.api.Validator;
import org.apache.sling.validation.api.ValidatorLookupService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component()
@Service(ValidatorLookupService.class)
@Reference(
        name = "validator",
        referenceInterface = Validator.class,
        policy = ReferencePolicy.DYNAMIC,
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE
)
public class ValidatorLookupServiceImpl implements ValidatorLookupService {

    Map<String, Validator> validators = new ConcurrentHashMap<String, Validator>();

    public Validator getValidator(String validatorType) {
        return validators.get(validatorType);
    }

    // OSGi ################################################################################################################################
    protected void bindValidator(Validator validator, Map<?, ?> properties) {
        String validatorType = (String) properties.get(Validator.SCR_PROP_NAME_VALIDATOR_TYPE);
        if (StringUtils.isNotEmpty(validatorType)) {
            validators.put(validatorType, validator);
        }
    }

    protected void unbindValidator(Validator validator, Map<?, ?> properties) {
        String validatorType = (String) properties.get(Validator.SCR_PROP_NAME_VALIDATOR_TYPE);
        if (StringUtils.isNotEmpty(validatorType)) {
            validators.remove(validatorType);
        }
    }
}