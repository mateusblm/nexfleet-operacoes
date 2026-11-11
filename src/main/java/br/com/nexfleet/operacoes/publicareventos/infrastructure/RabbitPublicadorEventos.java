package br.com.nexfleet.operacoes.publicareventos.infrastructure;

import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.publicareventos.application.EventoPendente;
import br.com.nexfleet.operacoes.publicareventos.application.PublicadorEventos;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nexfleet.messaging.provider", havingValue = "rabbit", matchIfMissing = true)
public class RabbitPublicadorEventos implements PublicadorEventos {
  private static final Logger LOG = LoggerFactory.getLogger(RabbitPublicadorEventos.class);
  private final RabbitTemplate rabbit;
  private final String exchange;
  private final Map<String, String> rotas;

  /** Só os eventos de registro; os demais tipos ficam sem rota. */
  public RabbitPublicadorEventos(
      RabbitTemplate rabbit, String exchange, String abastecimentoRoutingKey,
      String manutencaoRoutingKey) {
    this(rabbit, exchange, abastecimentoRoutingKey, manutencaoRoutingKey,
        null, null, null, null);
  }

  @Autowired
  public RabbitPublicadorEventos(
      RabbitTemplate rabbit,
      @Value("${nexfleet.eventos.exchange}") String exchange,
      @Value("${nexfleet.eventos.abastecimento-routing-key}") String abastecimentoRoutingKey,
      @Value("${nexfleet.eventos.manutencao-routing-key}") String manutencaoRoutingKey,
      @Value("${nexfleet.eventos.abastecimento-atualizado-routing-key}")
          String abastecimentoAtualizadoRoutingKey,
      @Value("${nexfleet.eventos.abastecimento-excluido-routing-key}")
          String abastecimentoExcluidoRoutingKey,
      @Value("${nexfleet.eventos.manutencao-atualizada-routing-key}")
          String manutencaoAtualizadaRoutingKey,
      @Value("${nexfleet.eventos.manutencao-excluida-routing-key}")
          String manutencaoExcluidaRoutingKey) {
    this.rabbit = rabbit;
    this.exchange = exchange;
    var rotas = new HashMap<String, String>();
    rotas.put("AbastecimentoRegistrado", abastecimentoRoutingKey);
    rotas.put("ManutencaoRegistrada", manutencaoRoutingKey);
    rotas.put("AbastecimentoAtualizado", abastecimentoAtualizadoRoutingKey);
    rotas.put("AbastecimentoExcluido", abastecimentoExcluidoRoutingKey);
    rotas.put("ManutencaoAtualizada", manutencaoAtualizadaRoutingKey);
    rotas.put("ManutencaoExcluida", manutencaoExcluidaRoutingKey);
    rotas.values().removeIf(Objects::isNull);
    this.rotas = Map.copyOf(rotas);
  }

  @Override
  public void publicar(EventoPendente evento) {
    try (MDC.MDCCloseable ignored =
        MDC.putCloseable(CorrelationIds.MDC_KEY, evento.correlationId().toString())) {
      publicarComCorrelacao(evento);
    }
  }

  private void publicarComCorrelacao(EventoPendente evento) {
    Message message = MessageBuilder
        .withBody(evento.payload().getBytes(StandardCharsets.UTF_8))
        .setContentType("application/json")
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        .setMessageId(evento.id().toString())
        .setHeader("eventoId", evento.id().toString())
        .setHeader("eventoTipo", evento.tipo())
        .setHeader("eventoVersao", evento.versao())
        .setHeader("agregadoTipo", evento.agregadoTipo())
        .setHeader("agregadoId", evento.agregadoId().toString())
        .setHeader("ocorridoEm", evento.ocorridoEm().toString())
        .setHeader(CorrelationIds.HEADER, evento.correlationId().toString())
        .build();
    CorrelationData confirmacao = new CorrelationData(evento.id().toString());
    rabbit.send(exchange, routingKey(evento), message, confirmacao);
    try {
      CorrelationData.Confirm confirm = confirmacao.getFuture().get(5, TimeUnit.SECONDS);
      if (!confirm.ack()) {
        throw new IllegalStateException("Broker recusou o evento: " + confirm.reason());
      }
      if (confirmacao.getReturned() != null) {
        throw new IllegalStateException("Nenhuma fila aceitou o evento.");
      }
      LOG.info("event_published eventId={} eventType={}", evento.id(), evento.tipo());
    } catch (InterruptedException falha) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Publicação interrompida.", falha);
    } catch (ExecutionException | TimeoutException falha) {
      throw new IllegalStateException("Confirmação do broker não recebida.", falha);
    }
  }

  private String routingKey(EventoPendente evento) {
    String rota = rotas.get(evento.tipo());
    if (rota == null)
      throw new IllegalArgumentException("Tipo de evento sem rota: " + evento.tipo());
    return rota;
  }
}
