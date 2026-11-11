package br.com.nexfleet.operacoes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** O OpenAPI é público (lido pela Swagger UI do Gateway); as operações continuam exigindo JWT. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1;databaseName=nexfleet;encrypt=false;loginTimeout=2",
    "spring.datasource.username=operacoes",
    "spring.datasource.password=indisponivel",
    "spring.flyway.enabled=false",
    "outbox.publicacao.atraso-inicial-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("jwt-rsa")
class OpenApiPublicoTest {
  @Autowired MockMvc http;

  @Test
  void openApiRespondeSemTokenComOsPathsDoServico() throws Exception {
    http.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.version").value("1.0.0"))
        .andExpect(jsonPath("$.servers[0].url").value("/"))
        .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/v1/operacoes/abastecimentos']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/operacoes/manutencoes']").exists());
  }

  @Test
  void operacaoDeNegocioContinuaRespondendo401SemToken() throws Exception {
    http.perform(get("/api/v1/operacoes/abastecimentos")).andExpect(status().isUnauthorized());
  }
}
