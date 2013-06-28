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
package org.apache.sling.validation.testservices;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.testing.integration.HttpTestBase;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ValidationServiceIT extends HttpTestBase {

    private static final String JSON_CONTENT_TYPE = "application/json";

    @Test
    public void testValidRequestModel1() throws IOException, JSONException {
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new NameValuePair("sling:resourceType", "validation/test/resourceType1"));
            add(new NameValuePair("field1", "HelloWorld"));
            add(new NameValuePair(SlingPostConstants.RP_OPERATION, "validation"));
        }};
        String response = getContent(getUrlForPath("/validation/testing/fakeFolder1/resource"), JSON_CONTENT_TYPE, params, HttpServletResponse.SC_OK, "POST");
        JSONObject jsonResponse = new JSONObject(response);
        assertTrue(jsonResponse.getBoolean("valid"));
    }

    @Test
    public void testInvalidRequestModel1() throws IOException, JSONException {
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new NameValuePair("sling:resourceType", "validation/test/resourceType1"));
            add(new NameValuePair("field1", "Hello World"));
            add(new NameValuePair(SlingPostConstants.RP_OPERATION, "validation"));
        }};
        String response = getContent(getUrlForPath("/validation/testing/fakeFolder1/resource"), JSON_CONTENT_TYPE, params, HttpServletResponse.SC_OK, "POST");
        JSONObject jsonResponse = new JSONObject(response);
        assertFalse(jsonResponse.getBoolean("valid"));
    }

    private String getUrlForPath(String path) {
        return HTTP_URL + path;
    }
}
