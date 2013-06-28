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
package org.apache.sling.validation.api;

import org.apache.sling.api.resource.ValueMap;

/**
 * The {@code ValidationService} provides methods for finding {@link ValidationModel} services.
 */
public interface ValidationService {

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
}
