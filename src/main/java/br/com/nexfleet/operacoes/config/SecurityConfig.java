package br.com.nexfleet.operacoes.config;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, Environment environment, ObjectProvider<JwtDecoder> decoders)
      throws Exception {
    boolean rsa = environment.acceptsProfiles(Profiles.of("jwt-rsa"));
    if (rsa) {
      var converter = new JwtAuthenticationConverter();
      converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        String perfil = jwt.getClaimAsString("perfil");
        if ("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil))
          return List.of(new SimpleGrantedAuthority("ROLE_" + perfil));
        return List.of();
      });
      http.oauth2ResourceServer(resource -> resource.jwt(jwt ->
          jwt.decoder(decoders.getObject()).jwtAuthenticationConverter(converter)));
    }
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/api/v1/operacoes/info")
                    .permitAll();
                var abastecimentos = auth.requestMatchers(
                    "/api/v1/operacoes/abastecimentos",
                    "/api/v1/operacoes/abastecimentos/**");
                if (rsa) abastecimentos.hasAnyRole("ADMINISTRADOR", "GESTOR_FROTA");
                else abastecimentos.denyAll();
                var manutencoes = auth.requestMatchers(
                    "/api/v1/operacoes/manutencoes",
                    "/api/v1/operacoes/manutencoes/**");
                if (rsa) manutencoes.hasAnyRole("ADMINISTRADOR", "GESTOR_FROTA");
                else manutencoes.denyAll();
                auth.anyRequest().denyAll();
              })
        .build();
  }
}
