package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import br.com.nexfleet.operacoes.registrarabastecimento.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcAbastecimentos implements Abastecimentos {
  private static final Logger LOG = LoggerFactory.getLogger(JdbcAbastecimentos.class);
  private static final String SELECT =
      "SELECT id,veiculo_id,motorista_id,realizado_em,combustivel,litros,valor_total,"
          + "quilometragem,criado_em,versao FROM operacoes.abastecimento";
  private static final RowMapper<Dados> MAP = (rs, row) -> new Dados(
      rs.getObject("id", UUID.class), rs.getObject("veiculo_id", UUID.class),
      rs.getObject("motorista_id", UUID.class),
      rs.getObject("realizado_em", OffsetDateTime.class).toInstant(),
      rs.getString("combustivel"), rs.getBigDecimal("litros"),
      rs.getBigDecimal("valor_total"), rs.getLong("quilometragem"),
      rs.getObject("criado_em", OffsetDateTime.class).toInstant(), rs.getInt("versao"));

  private static final JsonMapper JSON = JsonMapper.shared();

  private final JdbcTemplate jdbc;

  public JdbcAbastecimentos(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional
  public Dados registrar(UUID id, UUID veiculoId, UUID motoristaId, Instant realizadoEm,
      String combustivel, BigDecimal litros, BigDecimal valorTotal, long quilometragem,
      UUID autor, Instant criadoEm) {
    Long atual = travarHodometro(veiculoId);
    try {
      Abastecimento.validarContraHodometro(atual, quilometragem);
    } catch (AbastecimentoInvalido e) {
      throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.INVALIDO, e.getMessage());
    }
    var data = utc(realizadoEm);
    var criacao = utc(criadoEm);
    jdbc.update("INSERT INTO operacoes.abastecimento"
        + "(id,veiculo_id,motorista_id,realizado_em,combustivel,litros,valor_total,"
        + "quilometragem,criado_por,criado_em) VALUES (?,?,?,?,?,?,?,?,?,?)",
        id, veiculoId, motoristaId, data, combustivel, litros, valorTotal,
        quilometragem, autor, criacao);
    jdbc.update("UPDATE operacoes.hodometro SET quilometragem=?,atualizado_em=? WHERE veiculo_id=?",
        quilometragem, criacao, veiculoId);
    var registrado = buscar(id).orElseThrow();
    gravarEvento("AbastecimentoRegistrado", registrado, Map.of(), criacao);
    return registrado;
  }

  @Override
  @Transactional
  public Optional<Dados> atualizar(UUID id, String combustivel, BigDecimal litros,
      BigDecimal valorTotal, long quilometragem, UUID autor, Instant atualizadoEm) {
    var travado = travar(id);
    if (travado.isEmpty()) return Optional.empty();
    var atual = travado.get();
    try {
      faixaEntreVizinhos(atual).validar(quilometragem);
    } catch (AbastecimentoInvalido e) {
      throw new FalhaAbastecimento(FalhaAbastecimento.Tipo.INVALIDO, e.getMessage());
    }
    var momento = utc(atualizadoEm);
    jdbc.update("UPDATE operacoes.abastecimento SET combustivel=?,litros=?,valor_total=?,"
        + "quilometragem=?,versao=versao+1 WHERE id=?",
        combustivel, litros, valorTotal, quilometragem, id);
    recalcularHodometro(atual.veiculoId(), momento);
    var atualizado = buscar(id).orElseThrow();
    gravarEvento("AbastecimentoAtualizado", atualizado,
        Map.of("atualizadoPor", autor.toString()), momento);
    return Optional.of(atualizado);
  }

  @Override
  @Transactional
  public boolean excluir(UUID id, UUID autor, Instant excluidoEm) {
    var travado = travar(id);
    if (travado.isEmpty()) return false;
    var atual = travado.get();
    var momento = utc(excluidoEm);
    jdbc.update("DELETE FROM operacoes.abastecimento WHERE id=?", id);
    recalcularHodometro(atual.veiculoId(), momento);
    var campos = new LinkedHashMap<String, Object>();
    campos.put("abastecimentoId", id.toString());
    campos.put("veiculoId", atual.veiculoId().toString());
    campos.put("versao", Abastecimento.versaoSeguinte(atual.versao()));
    campos.put("excluidoPor", autor.toString());
    campos.put("excluidoEm", excluidoEm.toString());
    UUID correlationId = gravarOutbox("AbastecimentoExcluido", id, campos, momento);
    LOG.info("registro_excluido agregado=ABASTECIMENTO id={} autor={} correlationId={}",
        id, autor, correlationId);
    return true;
  }

  /**
   * Trava o hodômetro do veículo (mesma ordem de travas do registro) e depois a linha do
   * abastecimento; vazio se o abastecimento não existe.
   */
  private Optional<Dados> travar(UUID id) {
    var veiculoId = jdbc.query("SELECT veiculo_id FROM operacoes.abastecimento WHERE id=?",
        (rs, row) -> rs.getObject("veiculo_id", UUID.class), id).stream().findFirst();
    if (veiculoId.isEmpty()) return Optional.empty();
    travarHodometro(veiculoId.get());
    return jdbc.query(SELECT + " WITH (UPDLOCK, ROWLOCK) WHERE id=?", MAP, id)
        .stream().findFirst();
  }

  /** Semeia (se preciso) e trava a linha do hodômetro do veículo, devolvendo o km atual. */
  private Long travarHodometro(UUID veiculoId) {
    semearHodometro(veiculoId);
    return jdbc.queryForObject(
        "SELECT quilometragem FROM operacoes.hodometro WITH (UPDLOCK, ROWLOCK) WHERE veiculo_id=?",
        Long.class, veiculoId);
  }

  /** Km do abastecimento anterior e do seguinte do mesmo veículo (realizado_em, depois id). */
  private FaixaQuilometragem faixaEntreVizinhos(Dados atual) {
    var data = utc(atual.realizadoEm());
    var anterior = jdbc.query("SELECT TOP 1 quilometragem FROM operacoes.abastecimento "
        + "WHERE veiculo_id=? AND (realizado_em<? OR (realizado_em=? AND id<?)) "
        + "ORDER BY realizado_em DESC,id DESC",
        (rs, row) -> rs.getLong(1), atual.veiculoId(), data, data, atual.id())
        .stream().findFirst();
    var seguinte = jdbc.query("SELECT TOP 1 quilometragem FROM operacoes.abastecimento "
        + "WHERE veiculo_id=? AND (realizado_em>? OR (realizado_em=? AND id>?)) "
        + "ORDER BY realizado_em,id",
        (rs, row) -> rs.getLong(1), atual.veiculoId(), data, data, atual.id())
        .stream().findFirst();
    return new FaixaQuilometragem(anterior.orElse(null), seguinte.orElse(null));
  }

  /** Hodômetro = maior km dos abastecimentos do veículo; sem linha se não restar nenhum. */
  private void recalcularHodometro(UUID veiculoId, OffsetDateTime momento) {
    Long maior = jdbc.queryForObject("SELECT MAX(quilometragem) FROM operacoes.abastecimento "
        + "WHERE veiculo_id=?", Long.class, veiculoId);
    if (maior == null) {
      jdbc.update("DELETE FROM operacoes.hodometro WHERE veiculo_id=?", veiculoId);
      return;
    }
    jdbc.update("UPDATE operacoes.hodometro SET quilometragem=?,atualizado_em=? "
        + "WHERE veiculo_id=? AND quilometragem<>?", maior, momento, veiculoId, maior);
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

  /** Evento com os dados do abastecimento na versão gravada, na transação corrente. */
  private void gravarEvento(String tipoEvento, Dados abastecimento, Map<String, Object> extras,
      OffsetDateTime ocorridoEm) {
    var campos = new LinkedHashMap<String, Object>();
    campos.put("abastecimentoId", abastecimento.id().toString());
    campos.put("veiculoId", abastecimento.veiculoId().toString());
    campos.put("motoristaId", abastecimento.motoristaId().toString());
    campos.put("realizadoEm", abastecimento.realizadoEm().toString());
    campos.put("combustivel", abastecimento.combustivel());
    campos.put("litros", abastecimento.litros());
    campos.put("valorTotal", abastecimento.valorTotal());
    campos.put("quilometragem", abastecimento.quilometragem());
    campos.put("versao", abastecimento.versao());
    campos.putAll(extras);
    gravarOutbox(tipoEvento, abastecimento.id(), campos, ocorridoEm);
  }

  /** Grava na outbox com eventoId e correlationId no payload (envelope dos eventos atuais). */
  private UUID gravarOutbox(String tipoEvento, UUID agregadoId, Map<String, Object> campos,
      OffsetDateTime ocorridoEm) {
    UUID eventoId = UUID.randomUUID();
    UUID correlationId = CorrelationIds.currentUuidOrNew();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventoId", eventoId.toString());
    payload.putAll(campos);
    payload.put("correlationId", correlationId.toString());
    jdbc.update("INSERT INTO operacoes.outbox_evento(id,agregado_tipo,agregado_id,evento_tipo,"
        + "evento_versao,correlation_id,payload,ocorrido_em) "
        + "VALUES (?,'ABASTECIMENTO',?,?,1,?,?,?)",
        eventoId, agregadoId, tipoEvento, correlationId, JSON.writeValueAsString(payload),
        ocorridoEm);
    return correlationId;
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
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

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc.query(SELECT + " WHERE id=?", MAP, id).stream().findFirst();
  }
}
