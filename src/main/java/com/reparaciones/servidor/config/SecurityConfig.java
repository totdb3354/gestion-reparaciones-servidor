package com.reparaciones.servidor.config;

import com.reparaciones.servidor.security.ClaveSincronizadosFilter;
import com.reparaciones.servidor.security.ClaveVendidosFilter;
import com.reparaciones.servidor.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import com.reparaciones.servidor.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter          jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    private final ClaveVendidosFilter    claveVendidosFilter;
    private final ClaveSincronizadosFilter claveSincronizadosFilter;

    /** {@code pedidos.vendidos.clave} (variable PEDIDOS_VENDIDOS_CLAVE): la clave de maquina de GET /api/pedidos/vendidos; vacia = ruta apagada.
     *  {@code pedidos.sincronizados.clave} (PEDIDOS_SINCRONIZADOS_CLAVE): las claves que pueden ESCRIBIR en POST /api/pedidos/sincronizados (aviso del plugin de la web); aparte de la de lectura. */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsServiceImpl userDetailsService,
                          @Value("${pedidos.vendidos.clave:}") String claveVendidos,
                          @Value("${pedidos.sincronizados.clave:}") String claveSincronizados) {
        this.jwtAuthFilter     = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.claveVendidosFilter = new ClaveVendidosFilter(claveVendidos);
        this.claveSincronizadosFilter = new ClaveSincronizadosFilter(claveSincronizados);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        // Solo la clave de maquina (ClaveVendidosFilter) da este rol; ningun rol de usuario lo tiene.
                        .requestMatchers(ClaveVendidosFilter.RUTA).hasRole(ClaveVendidosFilter.ROL)
                        .requestMatchers(ClaveSincronizadosFilter.RUTA).hasRole(ClaveSincronizadosFilter.ROL)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(claveVendidosFilter, JwtAuthFilter.class)
                .addFilterBefore(claveSincronizadosFilter, JwtAuthFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
