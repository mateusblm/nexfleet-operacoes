package br.com.nexfleet.operacoes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1;databaseName=nexfleet;encrypt=false;loginTimeout=2",
    "spring.datasource.username=operacoes",
    "spring.datasource.password=indisponivel",
    "spring.flyway.enabled=false",
    "outbox.publicacao.atraso-inicial-ms=3600000"})
@AutoConfigureMockMvc
class ProntidaoSemBancoTest {
  @Autowired MockMvc http;

  @Test
  void readinessFicaDownComSqlServerIndisponivel() throws Exception {
    http.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }

  @Test
  void infoDoServicoResponde503ComSqlServerIndisponivel() throws Exception {
    http.perform(get("/api/v1/operacoes/info"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }
}
