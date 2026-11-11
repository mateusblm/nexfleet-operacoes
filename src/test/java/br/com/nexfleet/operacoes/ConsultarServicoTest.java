package br.com.nexfleet.operacoes;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.consultarservico.api.ServicoController;
import br.com.nexfleet.operacoes.consultarservico.application.ConsultarServico;
import org.junit.jupiter.api.Test;

class ConsultarServicoTest {
  @Test
  void confirmaServicoQuandoBancoResponde() {
    var response = new ServicoController(new ConsultarServico(() -> true)).consultar();
    assertEquals(200, response.getStatusCode().value());
    assertEquals("operacoes", response.getBody().service());
    assertEquals("UP", response.getBody().status());
  }

  @Test
  void naoAnunciaSaudeComBancoIndisponivel() {
    var response = new ServicoController(new ConsultarServico(() -> false)).consultar();
    assertEquals(503, response.getStatusCode().value());
    assertEquals("DOWN", response.getBody().status());
  }
}
