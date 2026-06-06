package com.org73n37.crudapp.infrastructure.web;

import com.org73n37.crudapp.api.BaseCrudController;
import com.org73n37.crudapp.api.UniversalCrudController;
import com.org73n37.crudapp.infrastructure.annotations.CrudResource;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [ARCHITECTURAL OPTIMIZATION]
 * Dynamically generates and registers Spring MVC/WebFlux RestControllers at startup
 * using Byte Buddy to enable native integration with Method Security, Request Interceptors,
 * Swagger, and other annotation-based framework capabilities.
 */
@Component
public class DynamicControllerRegister implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CrudResource.class));

        for (BeanDefinition bd : scanner.findCandidateComponents("com.org73n37.crudapp.data")) {
            try {
                String className = bd.getBeanClassName();
                Class<?> entityClass = Class.forName(className);
                CrudResource annotation = entityClass.getAnnotation(CrudResource.class);
                String path = annotation.path();
                String version = annotation.version();

                Class<?> controllerClass = new ByteBuddy()
                        .subclass(BaseCrudController.class)
                        .name("com.org73n37.crudapp.api.generated." + entityClass.getSimpleName() + "Controller")
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RestController.class).build(),
                                AnnotationDescription.Builder.ofType(RequestMapping.class)
                                        .defineArray("value", new String[]{"/api/" + path})
                                        .build()
                        )
                        .defineConstructor(Visibility.PUBLIC)
                        .withParameters(UniversalCrudController.class)
                        .intercept(MethodCall.invoke(
                                BaseCrudController.class.getDeclaredConstructor(UniversalCrudController.class, String.class))
                                .onSuper()
                                .withArgument(0)
                                .with(path)
                        )
                        .make()
                        .load(Thread.currentThread().getContextClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded();

                GenericBeanDefinition controllerBd = new GenericBeanDefinition();
                controllerBd.setBeanClass(controllerClass);
                controllerBd.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
                registry.registerBeanDefinition(entityClass.getSimpleName() + "Controller", controllerBd);

                org.slf4j.LoggerFactory.getLogger(DynamicControllerRegister.class)
                        .info("Generated and registered controller bean for: {} at /api/{}/{}", 
                                entityClass.getSimpleName(), version, path);

            } catch (Exception e) {
                throw new IllegalStateException("Failed to dynamically generate controller class", e);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No-op
    }
}
