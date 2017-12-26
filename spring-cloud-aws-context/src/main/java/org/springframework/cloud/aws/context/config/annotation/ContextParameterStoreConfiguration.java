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

package org.springframework.cloud.aws.context.config.annotation;

import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.cloud.aws.context.config.annotation.EnableParameterStoreProperties.AppendProfileToPathOption;
import org.springframework.cloud.aws.core.config.AmazonWebserviceClientConfigurationUtils;
import org.springframework.cloud.aws.core.env.ssm.ParameterStorePropertyNamingStrategy;
import org.springframework.cloud.aws.core.env.ssm.AwsParameterStorePropertySource;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;

/**
 * @author Ryohei Sonoda
 */
@Configuration
@Import(ContextDefaultConfigurationRegistrar.class)
public class ContextParameterStoreConfiguration {

    static final Logger LOGGER = LoggerFactory.getLogger(ContextParameterStoreConfiguration.class);

    public static class AwsSsmParameterStorePropertySourcePostProcessor
            implements PriorityOrdered, EnvironmentAware, BeanFactoryPostProcessor {

        private final AWSSimpleSystemsManagementClient client;

        private String path;

        private boolean recursive;

        private boolean withDecryption;

        private ParameterStorePropertyNamingStrategy namingStrategy;

        private AppendProfileToPathOption appendProfileToPath;

        private Environment environment;

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

        public void setAppendProfileToPath(AppendProfileToPathOption appendProfileToPath) {
            this.appendProfileToPath = appendProfileToPath;
        }

        public AwsSsmParameterStorePropertySourcePostProcessor(AWSSimpleSystemsManagementClient client) {
            this.client = client;
        }

        private String buildPath() {
            final String path;
            switch (this.appendProfileToPath) {
                case FIRST:
                    final String first = environment.getActiveProfiles()[0];
                    path = Paths.get(this.path).resolve(first).toString();
                    break;
                case ALL:
                    final String relativePath = StringUtils.arrayToDelimitedString(environment.getActiveProfiles(),
                            "/");
                    path = Paths.get(this.path).resolve(relativePath).toString();
                    break;
                default:
                    path = this.path;
            }
            return environment.resolvePlaceholders(path);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (!(this.environment instanceof ConfigurableEnvironment)) {
                LOGGER.warn("Environment is not of type '{}' property source with instance data is not available",
                        ConfigurableEnvironment.class.getName());
                return;
            }
            final AwsParameterStorePropertySource propertySource = new AwsParameterStorePropertySource(client);
            propertySource.setPath(buildPath());
            propertySource.setRecursive(recursive);
            propertySource.setWithDecryption(withDecryption);
            propertySource.setNamingStrategy(namingStrategy);
            final ConfigurableEnvironment environment = (ConfigurableEnvironment) this.environment;
            environment.getPropertySources().addFirst(propertySource);
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

    }

    public static class Registrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                BeanDefinitionRegistry registry) {
            final AnnotationAttributes annotationAttributes = AnnotationAttributes.fromMap(importingClassMetadata
                    .getAnnotationAttributes(EnableParameterStoreProperties.class.getName(), false));
            Assert.notNull(annotationAttributes,
                    "@EnableParameterStorePropertySource is not present on importing class "
                            + importingClassMetadata.getClassName());

            final BeanDefinitionHolder client = simpleSystemsManagementClient(registry);
            final BeanDefinitionHolder namingStrategy = namingStrategy(annotationAttributes.getClass("namingStrategy"),
                    registry);

            BeanDefinitionBuilder processorBeanDefinition = processorBeanDefinition(client,
                    annotationAttributes.getString("path"), annotationAttributes.getEnum("appendProfileToPath"),
                    annotationAttributes.getBoolean("recursive"), annotationAttributes.getBoolean("withDecryption"),
                    namingStrategy);

            BeanDefinitionReaderUtils.registerWithGeneratedName(processorBeanDefinition.getBeanDefinition(), registry);
        }

        private BeanDefinitionBuilder processorBeanDefinition(BeanDefinitionHolder client, String path,
                AppendProfileToPathOption appendProfileToPath, boolean recursive, boolean withDecryption,
                BeanDefinitionHolder namingStrategy) {
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
                    .rootBeanDefinition(AwsSsmParameterStorePropertySourcePostProcessor.class);
            beanDefinitionBuilder.addConstructorArgReference(client.getBeanName());
            beanDefinitionBuilder.addPropertyValue("path", path);
            beanDefinitionBuilder.addPropertyValue("recursive", recursive);
            beanDefinitionBuilder.addPropertyValue("withDecryption", withDecryption);
            beanDefinitionBuilder.addPropertyValue("appendProfileToPath", appendProfileToPath);
            beanDefinitionBuilder.addPropertyReference("namingStrategy", namingStrategy.getBeanName());
            return beanDefinitionBuilder;
        }

        private BeanDefinitionHolder simpleSystemsManagementClient(BeanDefinitionRegistry registry) {
            return AmazonWebserviceClientConfigurationUtils.registerAmazonWebserviceClient(this, registry,
                    AWSSimpleSystemsManagementClient.class.getName(), null, null);
        }

        private BeanDefinitionHolder namingStrategy(Class<?> namingStrategyClass, BeanDefinitionRegistry registry) {
            final BeanDefinition definition = BeanDefinitionBuilder.rootBeanDefinition(namingStrategyClass)
                    .getBeanDefinition();
            final BeanDefinitionHolder holder = new BeanDefinitionHolder(definition, namingStrategyClass.getName());
            BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
            return holder;
        }

    }

}
