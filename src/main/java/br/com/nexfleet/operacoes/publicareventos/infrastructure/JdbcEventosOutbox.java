package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.application.EventosOutbox;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventosOutbox implements EventosOutbox {
  private final JdbcTemplate jdbc;

  public JdbcEventosOutbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<EventoPendente> bloquearPendentes(int limite, Instant agora) {
    return jdbc.query(
        "SELECT id,agregado_tipo,agregado_id,evento_tipo,evento_versao,correlation_id,"
            + "payload::text AS payload,ocorrido_em,tentativas "
            + "FROM operacoes.outbox_evento "
            + "WHERE publicado_em IS NULL AND proxima_tentativa_em<=? "
            + "ORDER BY ocorrido_em,id FOR UPDATE SKIP LOCKED LIMIT ?",
        (rs, row) -> new EventoPendente(
            rs.getObject("id", UUID.class),
            rs.getString("agregado_tipo"),
            rs.getObject("agregado_id", UUID.class),
            rs.getString("evento_tipo"),
            rs.getInt("evento_versao"),
            rs.getObject("correlation_id", UUID.class),
            rs.getString("payload"),
            rs.getTimestamp("ocorrido_em").toInstant(),
            rs.getInt("tentativas")),
        Timestamp.from(agora),
        limite);
  }

  @Override
  public void marcarPublicado(UUID id, Instant publicadoEm) {
    jdbc.update(
        "UPDATE operacoes.outbox_evento SET publicado_em=?,tentativas=tentativas+1,"
            + "ultimo_erro=NULL WHERE id=? AND publicado_em IS NULL",
        Timestamp.from(publicadoEm),
        id);
  }

  @Override
  public void registrarFalha(UUID id, String erro, Instant proximaTentativaEm) {
    jdbc.update(
        "UPDATE operacoes.outbox_evento SET tentativas=tentativas+1,ultimo_erro=?,"
            + "proxima_tentativa_em=? WHERE id=? AND publicado_em IS NULL",
        erro,
        Timestamp.from(proximaTentativaEm),
        id);
  }
}
