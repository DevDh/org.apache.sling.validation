/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.validation.api;

/**
 * A {@code Validator} is responsible for validating a single piece of information according to an internal constraint.
 */
public interface Validator {

    /**
     * OSGi property name. Defines the validator's type, in order to select a certain validator type from all the registered validators.
     */
    static final String SCR_PROP_NAME_VALIDATOR_TYPE = "validator.type";

    /**
     * Validates the {@code data} according to the internal constraints of this validator.
     * 
     * @param data
     *            the data to validate
     * @return {@code true} if the data is valid, {@code false} otherwise
     */
    boolean validate(String data);

}