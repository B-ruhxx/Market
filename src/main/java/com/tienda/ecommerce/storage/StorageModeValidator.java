package com.tienda.ecommerce.storage;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Valida tempranamente que {@code app.storage.type} tenga un valor admitido ("local" o "gcs")
 * para evitar errores opacos de cableado al iniciar.
 */
@Component
public class StorageModeValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null) {
            return;
        }
        String type = environment.getProperty("app.storage.type");
        if (type == null) {
            return; // matchIfMissing=true activa el modo local por defecto
        }
        if (!"local".equals(type) && !"gcs".equals(type)) {
            throw new IllegalStateException(
                    "Configuración inválida: app.storage.type (APP_STORAGE_TYPE) debe ser 'local' o 'gcs', recibido: '" + type + "'"
            );
        }
    }
}
