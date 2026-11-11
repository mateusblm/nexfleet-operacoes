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
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAbastecimentosIT {
  private static final Instant AGORA = Instant.parse("2026-11-08T12:00:00Z");
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private JdbcAbastecimentos repositorio;
  private AbastecimentosController controller;

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

    jdbc = context.getBean(JdbcTemplate.class);
    repositorio = context.getBean(JdbcAbastecimentos.class);
    assertTrue(AopUtils.isAopProxy(repositorio),
        "O repositório precisa passar pelo proxy transacional do Spring.");

    ReferenciasFrota referenciasValidas = (token, veiculo, motorista, realizadoEm) -> true;
    var casoDeUso = new GerenciarAbastecimentos(
        repositorio, referenciasValidas, Clock.fixed(AGORA, ZoneOffset.UTC));
    controller = new AbastecimentosController(casoDeUso);
  }

  @AfterEach
  void limparBancoEfemero() {
    jdbc.execute("ALTER TABLE operacoes.outbox_evento "
        + "DROP CONSTRAINT IF EXISTS cq04_falha_injetada");
    jdbc.update("DELETE FROM operacoes.outbox_evento");
    jdbc.update("DELETE FROM operacoes.abastecimento");
    jdbc.update("DELETE FROM operacoes.hodometro");
  }

  @AfterAll
  void fecharContexto() {
    if (context != null) context.close();
  }

  @Test
  void cq03QuilometragemRegressivaRetorna422SemAlterarAsTresTabelas() {
    UUID veiculo = UUID.randomUUID();
    UUID motorista = UUID.randomUUID();
    jdbc.update("INSERT INTO operacoes.hodometro(veiculo_id,quilometragem) VALUES (?,?)",
        veiculo, 1_000L);

    FalhaAbastecimento falha = assertThrows(FalhaAbastecimento.class,
        () -> controller.registrar(jwt(), "Bearer teste-cq03",
            entrada(veiculo, motorista, 999L)));
    var resposta = controller.falha(falha);

    assertAll(
        () -> assertEquals(FalhaAbastecimento.Tipo.INVALIDO, falha.tipo()),
        () -> assertEquals(422, resposta.getStatusCode().value()),
        () -> assertTrue(resposta.getBody().get("message").contains("1000 km")),
        () -> assertEquals(0L, quantidadeAbastecimentos()),
        () -> assertEquals(0L, quantidadeEventos()),
        () -> assertEquals(1_000L, quilometragem(veiculo)));
  }

  @Test
  void cq04FalhaNaOutboxDesfazAbastecimentoHodometroEEvento() {
    UUID veiculo = UUID.randomUUID();
    UUID motorista = UUID.randomUUID();
    jdbc.execute("ALTER TABLE operacoes.outbox_evento "
        + "ADD CONSTRAINT cq04_falha_injetada "
        + "CHECK (agregado_tipo <> 'ABASTECIMENTO')");

    assertThrows(DataAccessException.class,
        () -> controller.registrar(jwt(), "Bearer teste-cq04",
            entrada(veiculo, motorista, 1_500L)));

    assertAll(
        () -> assertEquals(0L, quantidadeAbastecimentos()),
        () -> assertEquals(0L, quantidadeEventos()),
        () -> assertEquals(0L, quantidadeHodometros()),
        () -> assertFalse(repositorio.listar(veiculo, 0, 20).iterator().hasNext()));
  }

  private GerenciarAbastecimentos.Entrada entrada(
      UUID veiculo, UUID motorista, long quilometragem) {
    return new GerenciarAbastecimentos.Entrada(
        veiculo,
        motorista,
        AGORA.minus(Duration.ofHours(1)),
        "Diesel S10",
        new BigDecimal("42.50"),
        new BigDecimal("289.90"),
        quilometragem);
  }

  private Jwt jwt() {
    return Jwt.withTokenValue("token-integracao")
        .header("alg", "none")
        .subject(UUID.randomUUID().toString())
        .claim("perfil", "GESTOR_FROTA")
        .build();
  }

  private long quantidadeAbastecimentos() {
    return jdbc.queryForObject("SELECT count(*) FROM operacoes.abastecimento", Long.class);
  }

  private long quantidadeEventos() {
    return jdbc.queryForObject("SELECT count(*) FROM operacoes.outbox_evento", Long.class);
  }

  private long quantidadeHodometros() {
    return jdbc.queryForObject("SELECT count(*) FROM operacoes.hodometro", Long.class);
  }

  private long quilometragem(UUID veiculo) {
    return jdbc.queryForObject(
        "SELECT quilometragem FROM operacoes.hodometro WHERE veiculo_id=?",
        Long.class,
        veiculo);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TestDatabaseConfig {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          obrigatoria("NEXFLEET_INTEGRATION_DB_URL"),
          obrigatoria("NEXFLEET_INTEGRATION_DB_USER"),
          obrigatoria("NEXFLEET_INTEGRATION_DB_PASSWORD"));
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

    private static String obrigatoria(String nome) {
      String valor = System.getenv(nome);
      if (valor == null || valor.isBlank())
        throw new IllegalStateException("Variável obrigatória ausente: " + nome);
      return valor;
    }
  }
}
