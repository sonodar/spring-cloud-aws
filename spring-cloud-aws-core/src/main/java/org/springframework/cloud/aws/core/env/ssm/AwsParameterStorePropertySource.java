/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.aws.core.env.ssm;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.EnumerablePropertySource;

/**
 * @author Ryohei Sonoda
 */
public class AwsParameterStorePropertySource extends EnumerablePropertySource<Object> {

    static final String PARAMETER_STORE_PROPERTY_SOURCE_NAME = "ParameterStore";

    private final AWSSimpleSystemsManagementClient client;

    private String path;

    private boolean recursive;

    private boolean withDecryption;

    private ParameterStorePropertyNamingStrategy namingStrategy;

    private volatile Map<String, Object> cachedParameters;

    public AwsParameterStorePropertySource(AWSSimpleSystemsManagementClient client) {
        super(PARAMETER_STORE_PROPERTY_SOURCE_NAME, new Object());
        this.client = client;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public void setWithDecryption(boolean withDecryption) {
        this.withDecryption = withDecryption;
    }

    public void setNamingStrategy(ParameterStorePropertyNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    public Object getProperty(String name) {
        return getParameters().get(name);
    }

    private GetParametersByPathResult getParametersByPath(String nextToken) {
        GetParametersByPathRequest request = new GetParametersByPathRequest().withPath(path).withRecursive(recursive)
                .withWithDecryption(withDecryption);
        if (nextToken != null) {
            request = request.withNextToken(nextToken);
        }
        return client.getParametersByPath(request);
    }

    private void makeCachedParameters() {
        final Map<String, Object> parameterMap = new LinkedHashMap<>();
        String nextToken = null;
        while (true) {
            final GetParametersByPathResult result = getParametersByPath(nextToken);
            if (result.getParameters().isEmpty()) {
                break;
            }
            for (final Parameter parameter : result.getParameters()) {
                final String name = this.namingStrategy.getPropertyName(this.path, parameter);
                final Object value = getParameterValue(parameter);
                parameterMap.put(name, value);
            }
            if (result.getNextToken() == null) {
                break;
            }
            nextToken = result.getNextToken();
        }
        this.cachedParameters = Collections.unmodifiableMap(parameterMap);
    }

    private Object getParameterValue(Parameter parameter) {
        final ParameterType type = ParameterType.fromValue(parameter.getType());
        if (type != ParameterType.StringList) {
            return parameter.getValue();
        }
        return parameter.getValue().split(",");
    }

    private Map<String, Object> getParameters() {
        if (this.cachedParameters == null) {
            makeCachedParameters();
        }
        return this.cachedParameters;
    }

    @Override
    public String[] getPropertyNames() {
        if (this.cachedParameters == null) {
            makeCachedParameters();
        }
        return this.cachedParameters.keySet().toArray(new String[this.cachedParameters.size()]);
    }

}
