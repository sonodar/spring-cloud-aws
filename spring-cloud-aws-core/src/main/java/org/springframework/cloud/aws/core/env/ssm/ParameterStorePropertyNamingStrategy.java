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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.amazonaws.services.simplesystemsmanagement.model.Parameter;

/**
 * @author Ryohei Sonoda
 */
public interface ParameterStorePropertyNamingStrategy {

    String getPropertyName(String path, Parameter parameter);

    public abstract static class ParameterStorePropertyNamingStrategySupport
            implements ParameterStorePropertyNamingStrategy {

        protected final Pattern slash = Pattern.compile("/");

        protected String getRelativePath(String path, Parameter parameter) {
            return Paths.get(path).relativize(Paths.get(parameter.getName())).toString();
        }

        protected String[] getSplittedPath(String path, Parameter parameter) {
            return slash.split(getRelativePath(path, parameter));
        }

    }

    public static class SpringPropertyNamingStrategy extends ParameterStorePropertyNamingStrategySupport {
        @Override
        public String getPropertyName(String path, Parameter parameter) {
            final List<String> elements = new ArrayList<>();
            for (final String pathElement : getSplittedPath(path, parameter)) {
                elements.add(pathElement.replaceAll("_", "-"));
            }
            return StringUtils.collectionToDelimitedString(elements, ".");
        }
    }

    public static class BasenameNamingStrategy implements ParameterStorePropertyNamingStrategy {
        @Override
        public String getPropertyName(String path, Parameter parameter) {
            return Paths.get(parameter.getName()).getFileName().toString();
        }
    }

    public static class SnakeCaseNamingStrategy extends ParameterStorePropertyNamingStrategySupport {
        @Override
        public String getPropertyName(String path, Parameter parameter) {
            return slash.matcher(getRelativePath(path, parameter)).replaceAll("_").toUpperCase();
        }
    }

}
