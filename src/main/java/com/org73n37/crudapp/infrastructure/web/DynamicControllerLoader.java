package com.org73n37.crudapp.infrastructure.web;

import com.org73n37.crudapp.api.BaseCrudController;
import com.org73n37.crudapp.api.UniversalCrudController;
import com.org73n37.crudapp.infrastructure.annotations.CrudResource;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import com.org73n37.crudapp.logic.plugin.PluginLifecycleListener;
import java.lang.reflect.Method;

@Service
public class DynamicControllerLoader implements PluginLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(DynamicControllerLoader.class);
    
    private final ApplicationContext applicationContext;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public DynamicControllerLoader(ApplicationContext applicationContext, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.applicationContext = applicationContext;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * Programmatically compiles a new controller class using Byte Buddy, registers it as a Bean,
     * and binds it to the WebFlux RequestMappingHandlerMapping routing table.
     */
    public void registerControllerAtRuntime(Class<?> entityClass) throws Exception {
        CrudResource annotation = entityClass.getAnnotation(CrudResource.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Class must be annotated with @CrudResource");
        }

        String path = annotation.path();
        String beanName = entityClass.getSimpleName() + "Controller";

        log.info("🔨 Programmatically generating controller for resource: {} at /api/{}", entityClass.getSimpleName(), path);

        // 1. Generate Controller Class using Byte Buddy
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

        // 2. Register Bean Definition in ApplicationContext
        ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) applicationContext;
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) configurableContext.getBeanFactory();

        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(controllerClass);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        registry.registerBeanDefinition(beanName, beanDefinition);

        // 3. Register Handler Methods in WebFlux Routing Table
        Object controllerInstance = applicationContext.getBean(beanName);
        
        // Use reflection to call the protected detectHandlerMethods on RequestMappingHandlerMapping
        Method detectHandlerMethods = RequestMappingHandlerMapping.class
                .getDeclaredMethod("detectHandlerMethods", Object.class);
        detectHandlerMethods.setAccessible(true);
        detectHandlerMethods.invoke(requestMappingHandlerMapping, controllerInstance);

        log.info("✅ Dynamically registered WebFlux routes for: {}", beanName);
    }

    @Override
    public void onPluginLoaded(Class<?> pluginClass) throws Exception {
        registerControllerAtRuntime(pluginClass);
    }
}
