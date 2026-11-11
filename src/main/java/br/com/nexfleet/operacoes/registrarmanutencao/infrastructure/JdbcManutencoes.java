package br.com.nexfleet.operacoes.registrarmanutencao.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcManutencoes implements Manutencoes {
  private static final String SELECT = "SELECT id,veiculo_id,tipo,realizada_em,quilometragem,"
      + "descricao,oficina,valor,proxima_manutencao,criado_em,versao FROM operacoes.manutencao";
  private static final RowMapper<Dados> MAP = (rs, row) -> new Dados(
      rs.getObject("id", UUID.class), rs.getObject("veiculo_id", UUID.class),
      TipoManutencao.valueOf(rs.getString("tipo")), rs.getObject("realizada_em", LocalDate.class),
      rs.getLong("quilometragem"), rs.getString("descricao"), rs.getString("oficina"),
      rs.getBigDecimal("valor"), rs.getObject("proxima_manutencao", LocalDate.class),
      rs.getObject("criado_em", OffsetDateTime.class).toInstant(), rs.getInt("versao"));

  private static final JsonMapper JSON = JsonMapper.shared();

  private final JdbcTemplate jdbc;
  public JdbcManutencoes(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional
  public Dados registrar(UUID id, UUID veiculoId, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, UUID autor, Instant criadoEm) {
    var criacao = utc(criadoEm);
    jdbc.update("INSERT INTO operacoes.manutencao(id,veiculo_id,tipo,realizada_em,"
        + "quilometragem,descricao,oficina,valor,proxima_manutencao,criado_por,criado_em) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, veiculoId, tipo.name(), realizadaEm,
        quilometragem, descricao, oficina, valor, proximaManutencao, autor, criacao);
    var registrada = buscar(id).orElseThrow();
    gravarEvento("ManutencaoRegistrada", registrada, Map.of(), criacao);
    return registrada;
  }

  @Override
  @Transactional
  public Optional<Dados> atualizar(UUID id, TipoManutencao tipo, LocalDate realizadaEm,
      long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao, UUID autor, Instant atualizadoEm) {
    int alteradas = jdbc.update("UPDATE operacoes.manutencao SET tipo=?,realizada_em=?,"
        + "quilometragem=?,descricao=?,oficina=?,valor=?,proxima_manutencao=?,versao=versao+1 "
        + "WHERE id=?", tipo.name(), realizadaEm, quilometragem, descricao, oficina, valor,
        proximaManutencao, id);
    if (alteradas == 0) return Optional.empty();
    var atualizada = buscar(id).orElseThrow();
    gravarEvento("ManutencaoAtualizada", atualizada,
        Map.of("atualizadoPor", autor.toString()), utc(atualizadoEm));
    return Optional.of(atualizada);
  }

  /** Evento com os dados da manutenção na versão gravada, na transação corrente. */
  private void gravarEvento(String tipoEvento, Dados manutencao, Map<String, Object> extras,
      OffsetDateTime ocorridoEm) {
    var campos = new LinkedHashMap<String, Object>();
    campos.put("manutencaoId", manutencao.id().toString());
    campos.put("veiculoId", manutencao.veiculoId().toString());
    campos.put("tipo", manutencao.tipo().name());
    campos.put("realizadaEm", manutencao.realizadaEm().toString());
    campos.put("quilometragem", manutencao.quilometragem());
    campos.put("descricao", manutencao.descricao());
    campos.put("oficina", manutencao.oficina());
    campos.put("valor", manutencao.valor());
    campos.put("proximaManutencao", manutencao.proximaManutencao() == null
        ? null : manutencao.proximaManutencao().toString());
    campos.put("versao", manutencao.versao());
    campos.putAll(extras);
    gravarOutbox(tipoEvento, manutencao.id(), campos, ocorridoEm);
  }

  /** Grava na outbox com eventoId e correlationId no payload (envelope dos eventos atuais). */
  private void gravarOutbox(String tipoEvento, UUID agregadoId, Map<String, Object> campos,
      OffsetDateTime ocorridoEm) {
    UUID eventoId = UUID.randomUUID();
    UUID correlationId = CorrelationIds.currentUuidOrNew();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventoId", eventoId.toString());
    payload.putAll(campos);
    payload.put("correlationId", correlationId.toString());
    jdbc.update("INSERT INTO operacoes.outbox_evento(id,agregado_tipo,agregado_id,evento_tipo,"
        + "evento_versao,correlation_id,payload,ocorrido_em) "
        + "VALUES (?,'MANUTENCAO',?,?,1,?,?,?)",
        eventoId, agregadoId, tipoEvento, correlationId, JSON.writeValueAsString(payload),
        ocorridoEm);
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }

  @Override
  public List<Dados> listar(UUID veiculoId, int offset, int limite) {
    if (veiculoId == null)
      return jdbc.query(SELECT + " ORDER BY realizada_em DESC,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
          MAP, offset, limite);
    return jdbc.query(SELECT
        + " WHERE veiculo_id=? ORDER BY realizada_em DESC,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        MAP, veiculoId, offset, limite);
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc.query(SELECT + " WHERE id=?", MAP, id).stream().findFirst();
  }
}
