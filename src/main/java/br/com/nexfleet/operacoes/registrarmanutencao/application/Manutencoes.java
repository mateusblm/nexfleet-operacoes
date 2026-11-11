package br.com.nexfleet.operacoes.registrarmanutencao.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public interface Manutencoes {
  record Dados(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, Instant criadoEm) {}

  Dados registrar(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, UUID autor, Instant criadoEm);

  List<Dados> listar(UUID veiculoId, int offset, int limite);
}
