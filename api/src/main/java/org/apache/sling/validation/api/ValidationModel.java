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

import java.util.List;
import java.util.Set;

/**
 * A {@code ValidationModel} defines the validation rules that a resource tree has to pass.
 */
public interface ValidationModel {

    /**
     * Returns the properties validated by this model.
     *
     * @return the properties set
     */
    Set<ResourceProperty> getResourceProperties();

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

    /**
     * Returns the expected children for a resource validated by this model.
     *
     * @return the children list (can be empty if there are no children)
     */
    List<ChildResource> getChildren();

}
