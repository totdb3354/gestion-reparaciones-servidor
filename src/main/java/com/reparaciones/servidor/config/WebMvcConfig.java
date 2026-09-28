package com.reparaciones.servidor.config;

import com.reparaciones.servidor.security.RutasRetiradasInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RutasRetiradasInterceptor rutasRetiradas;

    public WebMvcConfig(RutasRetiradasInterceptor rutasRetiradas) {
        this.rutasRetiradas = rutasRetiradas;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rutasRetiradas).addPathPatterns("/api/**");
    }
}
