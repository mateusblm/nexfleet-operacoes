package br.com.nexfleet.operacoes.config;

import br.com.nexfleet.operacoes.consultarservico.application.ConsultarServico;
import br.com.nexfleet.operacoes.consultarservico.application.VerificarBanco;
import br.com.nexfleet.operacoes.observability.CorrelationIds;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import br.com.nexfleet.operacoes.registrarabastecimento.infrastructure.HttpReferenciasFrota;
import br.com.nexfleet.operacoes.registrarmanutencao.application.*;
import br.com.nexfleet.operacoes.registrarmanutencao.infrastructure.HttpVeiculosFrota;
import br.com.nexfleet.operacoes.publicareventos.application.*;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class UseCaseConfig {
  @Bean
  Clock relogio() { return Clock.systemUTC(); }

  @Bean
  RestClient frotaRestClient(@Value("${services.frota.url}") String url) {
    var factory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    return CorrelationIds.propagate(RestClient.builder())
        .baseUrl(url).requestFactory(factory).build();
  }

  @Bean
  GerenciarAbastecimentos gerenciarAbastecimentos(
      Abastecimentos abastecimentos, RestClient frotaRestClient, Clock relogio) {
    return new GerenciarAbastecimentos(
        abastecimentos, new HttpReferenciasFrota(frotaRestClient), relogio);
  }

  @Bean
  GerenciarManutencoes gerenciarManutencoes(
      Manutencoes manutencoes, RestClient frotaRestClient, Clock relogio) {
    return new GerenciarManutencoes(
        manutencoes, new HttpVeiculosFrota(frotaRestClient), relogio);
  }

  @Bean
  PublicarEventosPendentes publicarEventosPendentes(
      EventosOutbox outbox,
      PublicadorEventos publicador,
      Clock relogio,
      @Value("${outbox.publicacao.retry-segundos}") long retrySegundos) {
    return new PublicarEventosPendentes(
        outbox, publicador, relogio, Duration.ofSeconds(retrySegundos));
  }

  @Bean
  ConsultarServico consultarServico(VerificarBanco banco) {
    return new ConsultarServico(banco);
  }
}
