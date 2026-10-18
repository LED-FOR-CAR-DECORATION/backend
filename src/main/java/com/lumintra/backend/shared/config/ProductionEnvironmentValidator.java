package com.lumintra.backend.shared.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("prod")
public class ProductionEnvironmentValidator implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> missing = new ArrayList<>();

        // TODO: Implement env check for each key when running app on deployed machine

        if (!missing.isEmpty()) {
            StringBuilder message = new StringBuilder("Missing required production environment values:\n");
            missing.forEach(key -> message.append(" - ").append(key).append("\n"));
            throw new BeanCreationException(message.toString());
        }
    }

    private void validateRequired(String envKey, String propertyKey, List<String> missing) {
        String envValue = System.getenv(envKey);
        String systemPropertyValue = System.getProperty(envKey);
        String propertyValue;

        try {
            propertyValue = environment.getProperty(propertyKey);
        } catch (Exception ex) {
            // If a placeholder inside the property cannot resolve, treat it as missing
            propertyValue = null;
        }

        boolean isPresent = StringUtils.hasText(envValue)
                || StringUtils.hasText(systemPropertyValue)
                || StringUtils.hasText(propertyValue);

        if (!isPresent) {
            missing.add(envKey + " (or property '" + propertyKey + "')");
        }
    }

}