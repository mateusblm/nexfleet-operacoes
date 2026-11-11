# NexFleet — Operações (MS2, Azure SQL)

Microsserviço de abastecimentos e manutenções do NexFleet, plataforma de gestão de frotas. Parte da v1.0.0; o sistema completo está no repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet).

## Arquitetura

Operações é o **MS2** e persiste no **Azure SQL** (schema `operacoes`, login próprio, migrações Flyway). Ao registrar um abastecimento consulta Frota para validar o vínculo e garante que o hodômetro nunca regride; abastecimento, hodômetro e evento de saída são gravados **na mesma transação** (padrão outbox). Um job publica a outbox no **Azure Service Bus** (nuvem, tópico `nexfleet-eventos-v1`) ou no **RabbitMQ** (local), com leitura concorrente segura (`UPDLOCK, READPAST`). Os eventos `AbastecimentoRegistrado` v1 e `ManutencaoRegistrada` v1 são consumidos por Indicadores e pela Function. O BFF lê os históricos daqui para `GET /aggregated-data`. Rotas em `/api/v1/operacoes/{abastecimentos,manutencoes}`.

```text
Navegador → shell + microfrontends (Static Web Apps)
          → Azure API Management (APIM) → Gateway → BFF
              ├─ MS1 Frota ........ MongoDB Atlas
              ├─ MS2 Operações .... Azure SQL (schema operacoes)
              ├─ Identidade, Indicadores ... Azure SQL (um schema e um login por serviço)
              └─ Azure Function HTTP calcular-eficiencia
Operações → outbox → Service Bus (nuvem) / RabbitMQ (local) → Indicadores + Function (notificação)
```

**Clean Architecture + Vertical Slice.** Fatias em `br.com.nexfleet.operacoes`:

| Fatia | Camadas | Responsabilidade |
|---|---|---|
| `registrarabastecimento` | api, application, infrastructure | registrar e listar abastecimentos |
| `registrarmanutencao` | api, application, infrastructure | registrar e listar manutenções |
| `publicareventos` | application, infrastructure | publicar a outbox (Service Bus ou RabbitMQ) |
| `consultarservico` | api, application, infrastructure | informações do serviço |

Pacotes técnicos: `config` e `observability`.

**Testes:** unitários (ex.: `GerenciarAbastecimentosTest`, `PublicarEventosPendentesTest`, `ServiceBusPublicadorEventosTest`), de integração com SQL Server real em Testcontainers (`JdbcAbastecimentosIT`, `JdbcEventosOutboxIT`, `Cq05HistoricoVeiculoIT`) e de arquitetura com ArchUnit (`ArchitectureTest`).

## Tecnologias

- Java 21, Spring Boot 4.1.1 (Web MVC, Security, OAuth2 Resource Server, JDBC, AMQP, Flyway, Actuator)
- SQL Server / Azure SQL (`mssql-jdbc`, `flyway-sqlserver`)
- Azure SDK BOM 1.3.9 (`azure-messaging-servicebus`, `azure-identity`); RabbitMQ local
- springdoc-openapi 3.2.0 (OpenAPI em `/v3/api-docs`)
- Testcontainers (SQL Server)
- Micrometer + Prometheus
- JUnit 5, ArchUnit 1.4.1
- Maven Wrapper (Maven 3.9.16)
- Docker (`eclipse-temurin:21`), GitHub Actions (testes em todo push; imagem no Docker Hub em tag `v*`)

## Como rodar localmente

```bash
./mvnw -B -ntp verify                                    # unitários, ArchUnit e ITs (Testcontainers; exige Docker)
docker build -t mateusblm/nexfleet-operacoes:local .     # imagem (porta 8080)
```

Variáveis de ambiente: `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `FROTA_URL`, `IDENTIDADE_URL`, `MESSAGING_PROVIDER` (`rabbit` ou `servicebus`), `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `SERVICEBUS_FULLY_QUALIFIED_NAMESPACE`, `SERVICEBUS_TOPIC` e `SPRING_PROFILES_ACTIVE=jwt-rsa`.

**Ambiente completo** (todos os serviços, SQL Server, MongoDB e RabbitMQ locais) sobe pelo repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet), com os repositórios clonados lado a lado (`scripts/clonar-tudo.sh`):

```bash
scripts/iniciar-local.sh                                     # 1ª vez: gera o .env e a chave RSA local
docker compose -f compose.yaml -f compose.build.yaml up -d --build
scripts/seed-demo.sh                                         # dados de demonstração
```

Sem o override `compose.build.yaml`, o `docker compose up -d` usa as imagens publicadas no Docker Hub.

## URLs na nuvem

Operações não tem URL pública direta: é acessada só pelo APIM (via Gateway e BFF). O OpenAPI aparece na Swagger UI abaixo, opção "Operações"; os eventos, na página AsyncAPI.

Imagem Docker: [`mateusblm/nexfleet-operacoes:v1.0.0`](https://hub.docker.com/r/mateusblm/nexfleet-operacoes)

| Recurso | Endereço |
|---|---|
| Frontend | https://white-mushroom-0cadda010.3.azurestaticapps.net |
| API (APIM) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net |
| Swagger UI (BFF, Frota, Operações, Identidade, Indicadores) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/swagger-ui.html |
| Dados agregados (exige token) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/aggregated-data |
| AsyncAPI dos eventos | https://white-mushroom-0cadda010.3.azurestaticapps.net/docs/eventos/ |
| Repositório guarda-chuva | https://github.com/mateusblm/nexfleet |

> **Partida a frio:** os Container Apps escalam a zero e o Azure SQL pausa sem uso. A primeira chamada após um período de inatividade pode levar de 1 a 2 minutos (ou responder 503/504); tente de novo em seguida.

## Equipe

- Vinícius dos Reis Góes
- Mateus Burlamaqui
- Lucas Roussenq
- Erik Carvalho
