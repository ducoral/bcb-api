# BCB API — Big Chat Brasil

API REST da plataforma **Big Chat Brasil (BCB)**: comunicação entre empresas (clientes) e seus destinatários finais, com planos pré-pago e pós-pago, priorização de mensagens e geração automática de conversas.

Repositório: [https://github.com/ducoral/bcb-api](https://github.com/ducoral/bcb-api)

---

## Visão Geral

O BCB permite que empresas enviem mensagens (texto) para destinatários, com cobrança automática conforme o plano:

| Plano       | Comportamento principal                                      |
|-------------|--------------------------------------------------------------|
| **PREPAID** | Debita saldo antes de enfileirar a mensagem                  |
| **POSTPAID**| Consome limite mensal (renovado no dia 1º, fuso `America/Sao_Paulo`) |

Prioridades de mensagem:

| Prioridade | Custo   | Comportamento na fila                          |
|------------|---------|------------------------------------------------|
| `normal`   | R$ 0,25 | Processada em ordem FIFO dentro do seu peso    |
| `urgent`   | R$ 0,50 | Maior peso → processada prioritariamente       |

Ciclo de vida da mensagem: `QUEUED` → `PROCESSING` → `SENT` / `FAILED` (e estados posteriores `DELIVERED` / `READ` previstos no modelo).

O consumo da fila (worker de conversas) gera/atualiza registros em `conversation` e `conversation_message`.

---

## Stack Tecnológica

| Item                    | Versão / Detalhe                          |
|-------------------------|-------------------------------------------|
| Java                    | **25**                                    |
| Spring Boot             | **4.2.0-SNAPSHOT**                        |
| Apache Maven            | **3.9.15** (ou compatível)                |
| Banco de dados          | PostgreSQL 16                             |
| Migrações               | Flyway (`V1__create-tables.sql`)          |
| Documentação da API     | springdoc-openapi 3.x (Swagger UI)        |
| Autenticação            | JWT (jjwt) + token administrativo         |
| Containerização         | Docker multi-stage + docker-compose       |

---

## Pré-requisitos

- **Java 25** instalado e no `PATH`
- **Apache Maven 3.9.15** (ou superior compatível)
- **Docker** e **Docker Compose** (para execução com containers)
- Variável de ambiente `JWT_SECRET` (segredo para assinatura dos tokens JWT). Em desenvolvimento pode ser definida localmente.

---

## Como Executar

### 1. Banco de dados + aplicação via Docker Compose

```bash
# Na raiz do projeto (onde está docker-compose.yml)
docker compose up