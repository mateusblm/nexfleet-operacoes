package br.com.nexfleet.operacoes.registrarabastecimento.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface Abastecimentos {
  record Dados(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal,
      long quilometragem, Instant criadoEm, int versao) {
    public Dados(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
        String combustivel, BigDecimal litros, BigDecimal valorTotal,
        long quilometragem, Instant criadoEm) {
      this(id, veiculoId, motoristaId, realizadoEm, combustivel, litros, valorTotal,
          quilometragem, criadoEm, 1);
    }
  }

  Dados registrar(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal,
      long quilometragem, UUID autor, Instant criadoEm);

  List<Dados> listar(UUID veiculoId, int offset, int limite);

  Optional<Dados> buscar(UUID id);
}
