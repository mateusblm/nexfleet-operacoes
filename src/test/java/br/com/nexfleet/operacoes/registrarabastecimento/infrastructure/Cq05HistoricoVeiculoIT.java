package br.com.nexfleet.operacoes.registrarabastecimento.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.operacoes.registrarabastecimento.api.AbastecimentosController;
import br.com.nexfleet.operacoes.registrarabastecimento.application.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Cq05HistoricoVeiculoIT {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private static final Instant REALIZADO_EM = Instant.parse("2026-11-08T10:30:00Z");

  private AnnotationConfigApplicationContext context;
  private AbastecimentosController controller;
  private UUID veiculoId;
  private UUID motoristaId;
  private UUID autorId;

  @BeforeAll
  void prepararPostgresqlIsolado() {
    context = new AnnotationConfigApplicationContext(TestDatabaseConfig.class);
    DataSource dataSource = context.getBean(DataSource.class);
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("operacoes")
        .defaultSchema("operacoes")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    var repositorio = context.getBean(JdbcAbastecimentos.class);
    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, data) -> true;
    var casoDeUso = new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC));
    controller = new AbastecimentosController(casoDeUso);
    veiculoId = UUID.fromString(obrigatoria("NEXFLEET_CQ05_VEICULO_ID"));
    motoristaId = UUID.fromString(obrigatoria("NEXFLEET_CQ05_MOTORISTA_ID"));
    autorId = UUID.fromString(obrigatoria("NEXFLEET_CQ05_AUTOR_ID"));
  }

  @AfterAll
  void fecharContexto() {
    if (context != null) context.close();
  }

  @Test
  void cq05RegistrarOperacaoAntesDaInativacao() {
    var resposta = controller.registrar(
        jwt(),
        "Bearer teste-cq05",
        new GerenciarAbastecimentos.Entrada(
            veiculoId,
            motoristaId,
            REALIZADO_EM,
            "Diesel S10",
            new BigDecimal("35.50"),
            new BigDecimal("245.90"),
            1_250L));

    var registrado = resposta.getBody();
    assertNotNull(registrado);
    assertAll(
        () -> assertEquals(201, resposta.getStatusCode().value()),
        () -> assertEquals(veiculoId, registrado.veiculoId()),
        () -> assertEquals(motoristaId, registrado.motoristaId()),
        () -> assertEquals(REALIZADO_EM, registrado.realizadoEm()));
  }

  @Test
  void cq05ConsultarHistoricoDepoisDaInativacaoMantemDataEResponsavel() {
    var resposta = controller.listar(jwt(), veiculoId, 0, 20);
    var historico = resposta.getBody();

    assertNotNull(historico);
    var operacao = historico.stream()
        .filter(item -> item.veiculoId().equals(veiculoId))
        .findFirst()
        .orElseThrow();
    assertAll(
        () -> assertEquals(200, resposta.getStatusCode().value()),
        () -> assertEquals(REALIZADO_EM, operacao.realizadoEm()),
        () -> assertEquals(motoristaId, operacao.motoristaId()),
        () -> assertNotNull(operacao.criadoEm()));
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token-integracao-cq05")
        .header("alg", "none")
        .subject(autorId.toString())
        .claim("perfil", "GESTOR_FROTA")
        .build();
  }

  private static String obrigatoria(String nome) {
    String valor = System.getenv(nome);
    if (valor == null || valor.isBlank())
      throw new IllegalStateException("Variável obrigatória ausente: " + nome);
    return valor;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TestDatabaseConfig {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          obrigatoria("NEXFLEET_CQ05_OPERACOES_DB_URL"),
          obrigatoria("NEXFLEET_CQ05_OPERACOES_DB_USER"),
          obrigatoria("NEXFLEET_CQ05_OPERACOES_DB_PASSWORD"));
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    JdbcAbastecimentos jdbcAbastecimentos(JdbcTemplate jdbcTemplate) {
      return new JdbcAbastecimentos(jdbcTemplate);
    }
  }
}
