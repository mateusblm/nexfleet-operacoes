package br.com.nexfleet.operacoes.observability;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

public final class CorrelationIds {
  public static final String HEADER = "X-Correlation-ID";
  public static final String MDC_KEY = "correlationId";

  private CorrelationIds() {}

  public static String resolve(String candidate) {
    if (candidate != null && candidate.length() == 36) {
      try {
        return UUID.fromString(candidate).toString();
      } catch (IllegalArgumentException ignored) {
        // Gera um valor seguro abaixo.
      }
    }
    return UUID.randomUUID().toString();
  }

  public static String currentOrNew() {
    return resolve(MDC.get(MDC_KEY));
  }

  public static UUID currentUuidOrNew() {
    return UUID.fromString(currentOrNew());
  }

  public static RestClient.Builder propagate(RestClient.Builder builder) {
    return builder.requestInterceptor((request, body, execution) -> {
      request.getHeaders().set(HEADER, currentOrNew());
      return execution.execute(request, body);
    });
  }
}
