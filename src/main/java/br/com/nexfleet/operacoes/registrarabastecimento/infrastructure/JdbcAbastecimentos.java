package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAbastecimentos implements Abastecimentos {
  private static final String SELECT =
      "SELECT id,veiculo_id,motorista_id,realizado_em,combustivel,litros,valor_total,"
          + "quilometragem,criado_em FROM operacoes.abastecimento";
  private static final RowMapper<Dados> MAP = (rs, row) -> new Dados(
      rs.getObject("id", UUID.class), rs.getObject("veiculo_id", UUID.class),
      rs.getObject("motorista_id", UUID.class),
      rs.getObject("realizado_em", OffsetDateTime.class).toInstant(),
      rs.getString("combustivel"), rs.getBigDecimal("litros"),
      rs.getBigDecimal("valor_total"), rs.getLong("quilometragem"),
      rs.getObject("criado_em", OffsetDateTime.class).toInstant());

  private final JdbcTemplate jdbc;

  public JdbcAbastecimentos(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional
  public Dados registrar(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal, long quilometragem,
      UUID autor, Instant criadoEm) {
    jdbc.update("INSERT INTO operacoes.hodometro(veiculo_id,quilometragem) VALUES (?,0) "
        + "ON CONFLICT (veiculo_id) DO NOTHING", veiculoId);
    Long atual = jdbc.queryForObject(
        "SELECT quilometragem FROM operacoes.hodometro WHERE veiculo_id=? FOR UPDATE",
        Long.class, veiculoId);
    if (atual != null && quilometragem < atual)
      throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.INVALIDO,
          "A quilometragem não pode ser menor que o hodômetro atual de " + atual + " km.");
    var data = OffsetDateTime.ofInstant(realizadoEm, ZoneOffset.UTC);
    var criacao = OffsetDateTime.ofInstant(criadoEm, ZoneOffset.UTC);
    jdbc.update("INSERT INTO operacoes.abastecimento"
        + "(id,veiculo_id,motorista_id,realizado_em,combustivel,litros,valor_total,"
        + "quilometragem,criado_por,criado_em) VALUES (?,?,?,?,?,?,?,?,?,?)",
        id, veiculoId, motoristaId, data, combustivel, litros, valorTotal,
        quilometragem, autor, criacao);
    jdbc.update("UPDATE operacoes.hodometro SET quilometragem=?,atualizado_em=? WHERE veiculo_id=?",
        quilometragem, criacao, veiculoId);
    UUID eventoId = UUID.randomUUID();
    UUID correlationId = CorrelationIds.currentUuidOrNew();
    jdbc.update("INSERT INTO operacoes.outbox_evento"
        + "(id,agregado_tipo,agregado_id,evento_tipo,evento_versao,correlation_id,payload,ocorrido_em) "
        + "VALUES (?,'ABASTECIMENTO',?,'AbastecimentoRegistrado',1,?,"
        + "jsonb_build_object('eventoId',?,'abastecimentoId',?,'veiculoId',?,"
        + "'motoristaId',?,'realizadoEm',?,'combustivel',?,'litros',?,"
        + "'valorTotal',?,'quilometragem',?,'correlationId',?),?)",
        eventoId, id, correlationId, eventoId, id, veiculoId, motoristaId, data, combustivel,
        litros, valorTotal, quilometragem, correlationId, criacao);
    return buscar(id).orElseThrow();
  }

  @Override
  public List<Dados> listar(UUID veiculoId, int offset, int limite) {
    if (veiculoId == null)
      return jdbc.query(SELECT + " ORDER BY realizado_em DESC,id LIMIT ? OFFSET ?",
          MAP, limite, offset);
    return jdbc.query(SELECT
        + " WHERE veiculo_id=? ORDER BY realizado_em DESC,id LIMIT ? OFFSET ?",
        MAP, veiculoId, limite, offset);
  }

  private Optional<Dados> buscar(UUID id) {
    return jdbc.query(SELECT + " WHERE id=?", MAP, id).stream().findFirst();
  }
}
