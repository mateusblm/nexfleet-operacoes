package br.com.nexfleet.operacoes.registrarabastecimento.application;

import java.time.Instant;
import java.util.UUID;

public interface ReferenciasFrota {
  boolean operacaoPermitida(String token, UUID veiculoId, UUID motoristaId, Instant realizadoEm);
}
