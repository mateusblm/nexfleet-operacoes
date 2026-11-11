package br.com.nexfleet.operacoes.registrarmanutencao.infrastructure;

import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.util.UUID;
import org.springframework.web.client.*;

public class HttpVeiculosFrota implements VeiculosFrota {
  private final RestClient client;
  public HttpVeiculosFrota(RestClient client) { this.client = client; }

  @Override
  public boolean permiteManutencao(String token, UUID veiculoId) {
    if (token == null || !token.matches("Bearer [^\\s]+"))
      throw new FalhaManutencao(FalhaManutencao.Tipo.PROIBIDO, "Sessão inválida.");
    try {
      Veiculo resposta = client.get().uri("/api/v1/frota/veiculos/{id}", veiculoId)
          .header("Authorization", token).retrieve().body(Veiculo.class);
      if (resposta == null || resposta.id() == null || resposta.status() == null)
        throw indisponivel();
      return !"INATIVO".equals(resposta.status());
    } catch (RestClientResponseException erro) {
      int status = erro.getStatusCode().value();
      if (status == 401 || status == 403)
        throw new FalhaManutencao(FalhaManutencao.Tipo.PROIBIDO, "Acesso recusado por Frota.");
      if (status == 404) return false;
      throw indisponivel();
    } catch (RestClientException erro) { throw indisponivel(); }
  }

  private FalhaManutencao indisponivel() {
    return new FalhaManutencao(FalhaManutencao.Tipo.INDISPONIVEL,
        "Serviço Frota indisponível para validar o veículo.");
  }

  private record Veiculo(UUID id, String status) {}
}
