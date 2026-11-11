package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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

  private static final JsonMapper JSON = JsonMapper.shared();

  private final JdbcTemplate jdbc;

  public JdbcAbastecimentos(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional
  public Dados registrar(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal, long quilometragem,
      UUID autor, Instant criadoEm) {
    semearHodometro(veiculoId);
    Long atual = jdbc.queryForObject(
        "SELECT quilometragem FROM operacoes.hodometro WITH (UPDLOCK, ROWLOCK) WHERE veiculo_id=?",
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
        + "VALUES (?,'ABASTECIMENTO',?,'AbastecimentoRegistrado',1,?,?,?)",
        eventoId, id, correlationId,
        payload(eventoId, id, veiculoId, motoristaId, realizadoEm, combustivel, litros,
            valorTotal, quilometragem, correlationId),
        criacao);
    return buscar(id).orElseThrow();
  }

  /** Cria o hodômetro zerado do veículo se ainda não existir (semeadura idempotente). */
  private void semearHodometro(UUID veiculoId) {
    try {
      jdbc.update("MERGE operacoes.hodometro WITH (HOLDLOCK) AS h "
          + "USING (SELECT ? AS veiculo_id) AS s ON h.veiculo_id = s.veiculo_id "
          + "WHEN NOT MATCHED THEN INSERT (veiculo_id,quilometragem) VALUES (s.veiculo_id,0);",
          veiculoId);
    } catch (DuplicateKeyException corrida) {
      // Outra transação semeou o mesmo veículo; o hodômetro já existe.
    }
  }

  private static String payload(UUID eventoId, UUID id, UUID veiculoId, UUID motoristaId,
      Instant realizadoEm, String combustivel, BigDecimal litros, BigDecimal valorTotal,
      long quilometragem, UUID correlationId) {
    var campos = new LinkedHashMap<String, Object>();
    campos.put("eventoId", eventoId.toString());
    campos.put("abastecimentoId", id.toString());
    campos.put("veiculoId", veiculoId.toString());
    campos.put("motoristaId", motoristaId.toString());
    campos.put("realizadoEm", realizadoEm.toString());
    campos.put("combustivel", combustivel);
    campos.put("litros", litros);
    campos.put("valorTotal", valorTotal);
    campos.put("quilometragem", quilometragem);
    campos.put("correlationId", correlationId.toString());
    return JSON.writeValueAsString(campos);
  }

  @Override
  public List<Dados> listar(UUID veiculoId, int offset, int limite) {
    if (veiculoId == null)
      return jdbc.query(SELECT + " ORDER BY realizado_em DESC,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
          MAP, offset, limite);
    return jdbc.query(SELECT
        + " WHERE veiculo_id=? ORDER BY realizado_em DESC,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        MAP, veiculoId, offset, limite);
  }

  private Optional<Dados> buscar(UUID id) {
    return jdbc.query(SELECT + " WHERE id=?", MAP, id).stream().findFirst();
  }
}
