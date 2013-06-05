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

import org.apache.sling.api.resource.ValueMap;

import java.util.Set;

/**
 * A {@code ValidationModel} is responsible for checking that a {@link ValueMap}'s values are valid according to the model's internal
 * representation.
 */
public interface ValidationModel {

    /**
     * Returns the fields validated by this model.
     *
     * @return the fields set
     */
    Set<Field> getFields();

    /**
     * Returns the type of resource this model validates.
     *
     * @return the validated resource type
     */
    String getValidatedResourceType();

    /**
     * Returns the paths under which resources will be validated by this model.
     *
     * @return a path array
     */
    String[] getApplicablePaths();
}
