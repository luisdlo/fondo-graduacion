package mx.fondo.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Consultar es público (transparencia). Registrar ingresos y gastos pide login de vocal.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(antMatcher("/registro/**"), antMatcher("/ingresos"), antMatcher("/egresos")).authenticated()
                .anyRequest().permitAll())
            .formLogin(login -> login
                .loginPage("/login")
                .defaultSuccessUrl("/registro/tras-login", true)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .deleteCookies("JSESSIONID", "remember-me"))
            // Sesión persistente: al login queda una cookie "remember-me" válida por 30 días.
            // Aunque el vocal cierre el navegador, al regresar entra automáticamente.
            .rememberMe(rm -> rm
                .key("fondo-graduacion-remember-me-2026")
                .tokenValiditySeconds(30 * 24 * 60 * 60)
                .alwaysRemember(true))
            // La consola H2 (/h2) no manda token CSRF y se muestra en frames
            .csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * Password en texto plano por ahora (lo capturas tú en la BD).
     * Cuando quieras cifrarlo, guarda "{bcrypt}$2a$..." en la columna y funciona sin tocar código.
     */
    @Bean
    @SuppressWarnings("deprecation")
    PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
        return encoder;
    }
}
