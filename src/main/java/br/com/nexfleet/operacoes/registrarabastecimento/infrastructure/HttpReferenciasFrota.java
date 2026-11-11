package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.web.client.*;

public class HttpReferenciasFrota implements ReferenciasFrota {
  private final RestClient client;

  public HttpReferenciasFrota(RestClient client) { this.client = client; }

  @Override
  public boolean operacaoPermitida(String token, UUID veiculoId, UUID motoristaId, Instant data) {
    if (token == null || !token.matches("Bearer [^\\s]+"))
      throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.PROIBIDO, "Sessão inválida.");
    return chamada(() -> {
      Validacao resposta = client.get()
          .uri(builder -> builder.path("/api/v1/frota/vinculos/validacao-operacao")
              .queryParam("veiculoId", veiculoId)
              .queryParam("motoristaId", motoristaId)
              .queryParam("realizadoEm", data).build())
          .header("Authorization", token).retrieve().body(Validacao.class);
      if (resposta == null) throw indisponivel();
      return resposta.valido();
    });
  }

  private <T> T chamada(Supplier<T> request) {
    try { return request.get(); }
    catch (RestClientResponseException error) {
      if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403)
        throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.PROIBIDO, "Acesso recusado por Frota.");
      throw indisponivel();
    } catch (RestClientException error) { throw indisponivel(); }
  }

  private FalhaAbastecimento indisponivel() {
    return new FalhaAbastecimento(FalhaAbastecimento.Tipo.INDISPONIVEL,
        "Serviço Frota indisponível para validar o vínculo.");
  }

  private record Validacao(boolean valido) {}
}
