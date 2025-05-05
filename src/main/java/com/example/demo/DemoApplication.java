package com.example.demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

@SpringBootApplication
//@RegisterReflectionForBinding(MySuperAwesomeClass.class)
@ImportRuntimeHints(DemoApplication.Hints.class)
public class DemoApplication {

    @Component
    static class Cart implements Serializable {
    }

    @Bean
    static EmbabelBeanFactoryInitializationAotProcessor embabelBeanFactoryInitializationAotProcessor() {
        return new EmbabelBeanFactoryInitializationAotProcessor();
    }

    static class EmbabelBeanFactoryInitializationAotProcessor
            implements BeanFactoryInitializationAotProcessor {

        @Override
        public BeanFactoryInitializationAotContribution processAheadOfTime(
                ConfigurableListableBeanFactory beanFactory) {
            var serializableTypes = new HashSet<TypeReference>();
            for (var beanDefinitionName : beanFactory.getBeanDefinitionNames()) {
                var beanClass = Objects.requireNonNull(beanFactory.getType(beanDefinitionName));
                if (Serializable.class.isAssignableFrom(beanClass)) {
                    serializableTypes.add(TypeReference.of(beanClass));
                }
            }
            return (generationContext, _) -> {
                var runtimeHints = generationContext.getRuntimeHints();
                for (var serializableType : serializableTypes) {
                    runtimeHints.serialization().registerType(serializableType);
                    System.out.println("registering serializable type: " + serializableType + "");
                }
            };
        }
    }

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerResource(RESOURCE);
            hints.reflection().registerType(MySuperAwesomeClass.class, MemberCategory.values());
            hints.proxies().registerJdkProxy(
                    Foo.class,
                    org.springframework.aop.SpringProxy.class,
                    org.springframework.aop.framework.Advised.class,
                    org.springframework.core.DecoratingProxy.class);

        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    static final Resource RESOURCE = new ClassPathResource("/message");


    interface Foo {
        void bar();
    }

    @Bean
    ApplicationRunner runner() {
        return _ -> {
            var pfb = new ProxyFactoryBean();
            pfb.addInterface(Foo.class);
            pfb.addAdvice((MethodInterceptor) invocation -> {
                System.out.println("invoking " + invocation.getMethod().getName());
                return "hello";
            });
            var proxy = (Foo) pfb.getObject();
            proxy.bar();

            //
            var contentAsString = RESOURCE.getContentAsString(Charset.defaultCharset());
            System.out.println("content: " + contentAsString);

            var clzzStringName = Class.forName("com.example.demo.MySuperAwesomeClass");
            var clzzInstance = clzzStringName.getDeclaredConstructors()[0].newInstance();
            System.out.println(clzzInstance);

            var methods = clzzInstance.getClass().getDeclaredMethods();
            var mySuperMethod = Arrays
                    .stream(methods)
                    .filter(method -> method.getName().equals("mySuperMethod"))
                    .findFirst()
                    .get();
            System.out.println(mySuperMethod.invoke(clzzInstance));


        };
    }


}


class MySuperAwesomeClass {

    public String mySuperMethod() {
        return ("MySuperClass.mySuperMethod()");
    }
}

