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
    var criacao = OffsetDateTime.ofInstant(criadoEm, ZoneOffset.UTC);
    jdbc.update("INSERT INTO operacoes.manutencao(id,veiculo_id,tipo,realizada_em,"
        + "quilometragem,descricao,oficina,valor,proxima_manutencao,criado_por,criado_em) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, veiculoId, tipo.name(), realizadaEm,
        quilometragem, descricao, oficina, valor, proximaManutencao, autor, criacao);
    UUID eventoId = UUID.randomUUID();
    UUID correlationId = CorrelationIds.currentUuidOrNew();
    jdbc.update("INSERT INTO operacoes.outbox_evento(id,agregado_tipo,agregado_id,evento_tipo,"
        + "evento_versao,correlation_id,payload,ocorrido_em) "
        + "VALUES (?,'MANUTENCAO',?,'ManutencaoRegistrada',1,?,?,?)",
        eventoId, id, correlationId,
        payload(eventoId, id, veiculoId, tipo, realizadaEm, quilometragem, descricao, oficina,
            valor, proximaManutencao, correlationId),
        criacao);
    return buscar(id).orElseThrow();
  }

  private static String payload(UUID eventoId, UUID id, UUID veiculoId, TipoManutencao tipo,
      LocalDate realizadaEm, long quilometragem, String descricao, String oficina,
      BigDecimal valor, LocalDate proximaManutencao, UUID correlationId) {
    var campos = new LinkedHashMap<String, Object>();
    campos.put("eventoId", eventoId.toString());
    campos.put("manutencaoId", id.toString());
    campos.put("veiculoId", veiculoId.toString());
    campos.put("tipo", tipo.name());
    campos.put("realizadaEm", realizadaEm.toString());
    campos.put("quilometragem", quilometragem);
    campos.put("descricao", descricao);
    campos.put("oficina", oficina);
    campos.put("valor", valor);
    campos.put("proximaManutencao", proximaManutencao == null ? null : proximaManutencao.toString());
    campos.put("correlationId", correlationId.toString());
    return JSON.writeValueAsString(campos);
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
