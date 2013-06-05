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
package org.apache.sling.validation.impl.validators;

import java.util.regex.Pattern;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.validation.api.Validator;

@Component(metatype = true, label = "%alphacharactersvalidator.label", description = "%alphacharactersvalidator.description")
@Service(Validator.class)
@Properties({
    @Property(name = Validator.SCR_PROP_NAME_VALIDATOR_TYPE, value = "alphacharacters")
})
/**
 * The {@code AlphaCharactersValidator} validates that a {@link String} contains only letters, from any kind of language.
 */
public class AlphaCharactersValidator implements Validator {

    static final String LETTERS_ONLY_REGEX = "^\\p{L}+$";
    static final Pattern pattern = Pattern.compile(LETTERS_ONLY_REGEX);

    public boolean validate(String data) {
        return pattern.matcher(data).matches();
    }

}
