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
package org.apache.sling.validation.impl.setup;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.testing.jcr.RepositoryProvider;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MockedResourceResolver implements ResourceResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MockedResourceResolver.class);

    private static final String[] SEARCH_PATHS = new String[] {"/apps", "/libs"};

    public final RepositoryProvider repoProvider;
    private List<MockedResource> resources = new LinkedList<MockedResource>();

    private Session session;

    public MockedResourceResolver() throws Exception {
        this.repoProvider = RepositoryProvider.instance();
        createSession();
        RepositoryUtil.registerSlingNodeTypes(session);
    }

    public Session createSession() throws RepositoryException {
        synchronized (this) {
            if (session != null) {
                return session;
            }
            session = repoProvider.getRepository().loginAdministrative(null);
            return session;
        }
    }

    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type.equals(Session.class)) {
            try {
                return (AdapterType) createSession();
            } catch (RepositoryException e) {
                throw new RuntimeException("RepositoryException: " + e, e);
            }
        } else if (type.equals(Repository.class)) {
            try {
                return (AdapterType) repoProvider.getRepository();
            } catch (RepositoryException e) {
                throw new RuntimeException("RepositoryException: " + e, e);
            }
        }
        throw new UnsupportedOperationException("Not implemented");
    }

    public Resource resolve(HttpServletRequest request, String absPath) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Resource resolve(String absPath) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Deprecated
    public Resource resolve(HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String map(String resourcePath) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String map(HttpServletRequest request, String resourcePath) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Resource getResource(String path) {
        Session session;
        try {
            session = createSession();
            session.getNode(path);
        } catch (PathNotFoundException e) {
            return null;
        } catch (RepositoryException e) {
            throw new RuntimeException("RepositoryException: " + e, e);
        }
        return new MockedResource(this, path, "nt:unstructured");
    }

    public Resource getResource(Resource base, String path) {
        if (base.getPath().equals("/")) {
            return getResource("/" + path);
        } else {
            return getResource(base.getPath() + "/" + path);
        }
    }

    public String[] getSearchPath() {
        return SEARCH_PATHS;
    }

    public Iterator<Resource> listChildren(Resource parent) {
        try {
            Node node = parent.adaptTo(Node.class);
            final NodeIterator nodes = node.getNodes();
            return new Iterator<Resource>() {

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public Resource next() {
                    Node next = nodes.nextNode();
                    try {
                        return new MockedResource(MockedResourceResolver.this,
                                next.getPath(), "nt:unstructured");
                    } catch (RepositoryException e) {
                        throw new RuntimeException("RepositoryException: " + e,
                                e);
                    }
                }

                public boolean hasNext() {
                    return nodes.hasNext();
                }
            };
        } catch (RepositoryException e) {
            throw new RuntimeException("RepositoryException: " + e, e);
        }
    }

    public Iterable<Resource> getChildren(Resource parent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Iterator<Resource> findResources(String query, String language) {
        List<Resource> resources = new ArrayList<Resource>();
        try {
            NodeIterator iterator = session.getWorkspace().getQueryManager().createQuery(query, language).execute().getNodes();
            while (iterator.hasNext()) {
                Node n = iterator.nextNode();
                Resource resource = new MockedResource(this, n);
                resources.add(resource);
            }
        } catch (RepositoryException e) {
            LOG.error("Unable to execute JCR query", e);
        }
        return resources.iterator();
    }

    public Iterator<Map<String, Object>> queryResources(String query,
            String language) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public ResourceResolver clone(Map<String, Object> authenticationInfo)
            throws LoginException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isLive() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void close() {
        Iterator<MockedResource> it = resources.iterator();
        while (it.hasNext()) {
            MockedResource r = it.next();
            r.close();
        }
        if (session != null) {
            if (session.isLive()) {
                session.logout();
            }
            session = null;
        }
    }

    public void register(MockedResource mockedResource) {
        resources.add(mockedResource);
    }

    public String getUserID() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Iterator<String> getAttributeNames() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Object getAttribute(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void delete(Resource resource) throws PersistenceException {
        if (resources.contains(resource)) {
            resources.remove(resource);
        }
        Node node = resource.adaptTo(Node.class);
        try {
            node.remove();
        } catch (RepositoryException e) {
            throw new PersistenceException("RepositoryException: "+e, e);
        }
    }

    public Resource create(Resource parent, String name,
            Map<String, Object> properties) throws PersistenceException {
        final Node parentNode = parent.adaptTo(Node.class);
        try {
            final Node child;
            if (properties!=null && properties.containsKey("jcr:primaryType")) {
                child = parentNode.addNode(name, (String) properties.get("jcr:primaryType"));
            } else {
                child = parentNode.addNode(name);
            }
            if (properties!=null) {
                final Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
                while(it.hasNext()) {
                    final Entry<String, Object> entry = it.next();
                    if (entry.getKey().equals("jcr:primaryType")) {
                        continue;
                    }
                    if (entry.getValue() instanceof String) {
                        child.setProperty(entry.getKey(), (String)entry.getValue());
                    } else if (entry.getValue() instanceof Boolean) {
                        child.setProperty(entry.getKey(), (Boolean)entry.getValue());
                    } else if (entry.getValue() instanceof  String[]) {
                        child.setProperty(entry.getKey(), (String[]) entry.getValue());
                    } else {
                        throw new UnsupportedOperationException("Not implemented");
                    }
                }
            }
            return getResource(parent, name);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    public void revert() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void commit() throws PersistenceException {
        try {
            this.session.save();
        } catch (final RepositoryException re) {
            throw new PersistenceException("Unable to commit changes.", re);
        }
    }

    public boolean hasChanges() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String getParentResourceType(Resource resource) {
        // TODO Auto-generated method stub
        return null;
    }

    public String getParentResourceType(String resourceType) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isResourceType(Resource resource, String resourceType) {
        // TODO Auto-generated method stub
        return false;
    }

    public void refresh() {
        // TODO Auto-generated method stub

    }
}
