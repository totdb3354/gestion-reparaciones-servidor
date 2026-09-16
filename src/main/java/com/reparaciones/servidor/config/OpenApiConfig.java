package com.reparaciones.servidor.config;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Map;

/**
 * Ajustes del contrato OpenAPI que publica springdoc.
 *
 * <p>Por defecto springdoc nombra cada esquema con el <em>nombre simple</em> de la clase, y los
 * controllers de este proyecto declaran los cuerpos de petición como records anidados cuyos nombres
 * se repiten (hay 6 {@code InsertarRequest}, 5 {@code EditarRequest}, 3 {@code ActivoRequest}…).
 * Al colisionar gana uno solo: {@code components.schemas.EditarRequest} acababa siendo el de
 * {@code ReparacionController} mientras {@code PUT /api/clientes/{idCli}} lo referenciaba, así que
 * los tipos TypeScript que la app web genera del contrato salían equivocados.
 *
 * <p>Por eso se sustituye el {@link ModelResolver} de swagger-core por uno con un
 * {@link TypeNameResolver} propio que prefija los anidados con el nombre del controller que los
 * contiene ({@code ClienteController.EditarRequest} → {@code ClienteEditarRequest}). Es una regla
 * genérica: vale para todos los controllers, sin anotar record por record. Las clases de primer
 * nivel (las de {@code model/}) conservan su nombre.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Reemplaza el resolutor de modelos de swagger-core por uno que nombra los esquemas con
     * {@link NombreEsquemaAnidado}. Se queda con el {@code ObjectMapper} de springdoc para no
     * cambiar nada más del contrato.
     *
     * <p>Se declara con la máxima precedencia para que {@code ModelConverterRegistrar} lo registre
     * el primero: {@code ModelConverters.addConverter} inserta por delante, así que registrarlo
     * antes que los demás lo deja al final de la cadena, en el sitio que ocupaba el resolutor por
     * defecto al que sustituye.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ModelResolver modelResolver(ObjectMapperProvider objectMapperProvider) {
        return new ModelResolver(objectMapperProvider.jsonMapper(), new NombreEsquemaAnidado());
    }

    /**
     * Jackson serializa todas las claves (inclusión por defecto del proyecto), así que para la web cada
     * propiedad está siempre presente: se marcan todas como {@code required} y la nulabilidad se declara
     * campo a campo con {@code @Schema(nullable = true)}. openapi-typescript genera entonces {@code T} o
     * {@code T | null} en vez de {@code T | undefined}, y client.ts deja de necesitar {@code Required<>}.
     */
    @Bean
    OpenApiCustomizer todasLasPropiedadesRequeridas() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            for (Schema<?> esquema : openApi.getComponents().getSchemas().values()) {
                Map<String, Schema> propiedades = esquema.getProperties();
                if (propiedades == null || propiedades.isEmpty()) continue;
                esquema.setRequired(new ArrayList<>(propiedades.keySet()));
            }
        };
    }

    /**
     * Nombra los esquemas de las clases anidadas como {@code <controller sin sufijo><Clase>} para
     * que no choquen entre controllers; las de primer nivel mantienen su nombre simple.
     *
     * <p>Solo se sobrescribe {@code getNameOfClass}, que es el paso que devuelve el nombre plano:
     * {@code nameForClass} sigue siendo el de swagger-core, de modo que un {@code @Schema(name=…)}
     * explícito en la clase continúa teniendo prioridad sobre esta regla.
     */
    static class NombreEsquemaAnidado extends TypeNameResolver {

        private static final String SUFIJO_CONTROLLER = "Controller";

        @Override
        protected String getNameOfClass(Class<?> cls) {
            Class<?> envolvente = cls.getEnclosingClass();
            // getUseFqn() es defensivo: springdoc 2.6 aplica springdoc.use-fqn al TypeNameResolver.std
            // estático, no a este bean, así que en la práctica esta rama nunca se toma hoy.
            if (envolvente == null || cls.getSimpleName().isEmpty() || getUseFqn()) {
                return super.getNameOfClass(cls);
            }
            return prefijo(envolvente) + cls.getSimpleName();
        }

        /** Nombre de la envolvente sin el sufijo {@code Controller}; recursivo si hay varios niveles. */
        private static String prefijo(Class<?> cls) {
            String propio = cls.getSimpleName();
            if (propio.endsWith(SUFIJO_CONTROLLER)) {
                propio = propio.substring(0, propio.length() - SUFIJO_CONTROLLER.length());
            }
            Class<?> envolvente = cls.getEnclosingClass();
            return envolvente == null ? propio : prefijo(envolvente) + propio;
        }
    }
}
