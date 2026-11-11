package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.application.EventosOutbox;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

  /**
   * Bloqueia até {@code limite} eventos pendentes para a transação corrente. UPDLOCK segura as
   * linhas até o commit e READPAST pula as que outro publicador já bloqueou. O hint de índice e
   * a ordem (proxima_tentativa_em, ocorrido_em, id) casam com {@code ix_outbox_pendente} para o
   * plano dispensar o Sort: com ele, o TOP só corta depois de ler (e bloquear) todos os
   * pendentes, e um segundo publicador não acharia nada (READPAST deixaria de dividir o lote).
   */
  @Override
  public List<EventoPendente> bloquearPendentes(int limite, Instant agora) {
    return jdbc.query(
        "SELECT TOP (?) id,agregado_tipo,agregado_id,evento_tipo,evento_versao,correlation_id,"
            + "payload,ocorrido_em,tentativas "
            + "FROM operacoes.outbox_evento "
            + "WITH (INDEX(ix_outbox_pendente), UPDLOCK, READPAST, ROWLOCK) "
            + "WHERE publicado_em IS NULL AND proxima_tentativa_em<=? "
            + "ORDER BY proxima_tentativa_em,ocorrido_em,id",
        (rs, row) -> new EventoPendente(
            rs.getObject("id", UUID.class),
            rs.getString("agregado_tipo"),
            rs.getObject("agregado_id", UUID.class),
            rs.getString("evento_tipo"),
            rs.getInt("evento_versao"),
            rs.getObject("correlation_id", UUID.class),
            rs.getString("payload"),
            rs.getObject("ocorrido_em", OffsetDateTime.class).toInstant(),
            rs.getInt("tentativas")),
        limite,
        utc(agora));
  }

  @Override
  public void marcarPublicado(UUID id, Instant publicadoEm) {
    jdbc.update(
        "UPDATE operacoes.outbox_evento SET publicado_em=?,tentativas=tentativas+1,"
            + "ultimo_erro=NULL WHERE id=? AND publicado_em IS NULL",
        utc(publicadoEm),
        id);
  }

  @Override
  public void registrarFalha(UUID id, String erro, Instant proximaTentativaEm) {
    jdbc.update(
        "UPDATE operacoes.outbox_evento SET tentativas=tentativas+1,ultimo_erro=?,"
            + "proxima_tentativa_em=? WHERE id=? AND publicado_em IS NULL",
        erro,
        utc(proximaTentativaEm),
        id);
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }
}
