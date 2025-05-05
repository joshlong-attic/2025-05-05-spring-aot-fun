package com.example.demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
    ApplicationRunner runner(LoggedTargetInstance loggedTargetInstance ) {
        return _ -> {


            loggedTargetInstance.doSomethingInCglib();

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

    @Bean
    static LoggedBeanPostProcessor loggedBeanPostProcessor() {
        return new LoggedBeanPostProcessor();
    }

}


class LoggedBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor {


    private static ProxyFactory proxyFactory(Object target, Class<?> targetClass) {
        var pf = new ProxyFactory();
        pf.setTargetClass(targetClass);
        pf.setInterfaces(targetClass.getInterfaces());
        pf.setProxyTargetClass(true); // <4>
        pf.addAdvice((MethodInterceptor) invocation -> {
            var methodName = invocation.getMethod().getName();
            System.out.println("before " + methodName);
            var result = invocation.getMethod().invoke(target, invocation.getArguments());
            System.out.println("after " + methodName);
            return result;
        });
        if (null != target) {
            pf.setTarget(target);
        }
        return pf;
    }


    private static boolean matches(Class<?> clazzName) {
        return clazzName != null && (clazzName.getAnnotation(Logged.class) != null || ReflectionUtils
                .getUniqueDeclaredMethods(clazzName, method -> method.getAnnotation(Logged.class) != null).length > 0);
    }


    @Override
    public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
        if (matches(beanClass)) {
            return proxyFactory(null, beanClass).getProxyClass(beanClass.getClassLoader());
        }
        return beanClass;
    }


    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        var beanClass = bean.getClass();
        if (matches(beanClass)) {
            return proxyFactory(bean, beanClass).getProxy(beanClass.getClassLoader());
        }
        return bean;
    }

}

@Inherited
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Logged {

}

@Logged
@Service
class LoggedTargetInstance {

    public String doSomethingInCglib() {
        return "Hello World!";
    }
}

class MySuperAwesomeClass {

    public String mySuperMethod() {
        return ("MySuperClass.mySuperMethod()");
    }
}

