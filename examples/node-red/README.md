# Flows do Node-RED

Flows do Node-RED que usam o Agent Server.

## Book Keeper (Telegram)

Atendente de livraria no Telegram. É a versão para Node-RED do flow [`n8n-flows/book-keeper.json`](../n8n-flows/book-keeper.json), com três trocas:
- o Telegram no lugar do WhatsApp;
- o PostgreSQL do Agent Server (schema `bookkeeper`) no lugar do Baserow;
- a API de Assistants do Agent Server no lugar da OpenAI.

| Arquivo | O que é |
| --- | --- |
| `book-keeper-flow.json` | o flow, para importar |
| `book-keeper-schema.sql` | schema `bookkeeper`: `users` e `books` (catálogo fictício) |
| `book-keeper-assistant.json` | corpo do `POST /v1/assistants`, com a tool `get_books` |

Testado com Node-RED 5.0.9, `node-red-contrib-postgresql` 0.16.2 e `node-red-contrib-telegrambot` 19.0.3, com o Agent Server em `AGENT_STORE=postgres`.

### Como funciona

1. **Entrada:** o `telegram receiver` recebe a mensagem; só mensagens de texto seguem.
2. **Usuário:** um único `INSERT … ON CONFLICT (telegram_id) DO UPDATE … RETURNING` busca ou cria o usuário em `bookkeeper.users`. Isso faz o papel dos nós de buscar, testar, criar e juntar do Baserow.
3. **Thread:**
   - sem thread, cria uma com `POST /threads` e a grava no usuário;
   - com thread e uma run ativa nela, cancela a run e espera ela terminar, porque o servidor recusa mensagem nova com run ativa;
   - se a thread não existe mais (404, por exemplo num servidor sem persistência), cria outra.
4. **Run:** adiciona a mensagem, cria a run com `BOOKKEEPER_ASSISTANT_ID` e consulta o status a cada 1 s, por no máximo 90 vezes.
5. **Tool:** em `requires_action`, cada `get_books` vira uma consulta em `bookkeeper.books` pela categoria (`ILIKE`). Os resultados voltam por `submit_tool_outputs`, e uma tool desconhecida recebe "Unknown tool".
6. **Resposta:** com a run `completed`, a última mensagem da thread vai para o chat pelo `telegram sender`. Os outros finais (`failed`, `expired`…) e qualquer erro, capturado pelo nó `catch`, mandam um aviso curto ao usuário.

### Instalação

1. **Pacotes do Node-RED** (Manage palette, ou `npm install` na pasta de dados):
   - `node-red-contrib-postgresql` 0.16.2;
   - `node-red-contrib-telegrambot`.
2. **Banco:** rode o schema no PostgreSQL do Agent Server. É o mesmo banco do `AGENT_STORE=postgres`; as tabelas ficam no schema próprio `bookkeeper` e não se misturam com as do servidor.
   ```bash
   docker compose exec -T postgres psql -U agent-server -d agent-server < examples/node-red/book-keeper-schema.sql
   ```
3. **Assistant:** cadastre-o e guarde o `id` que voltar.
   ```bash
   curl -s -X POST http://localhost:8080/v1/assistants -H "Authorization: Bearer $KEY" \
     -H "Content-Type: application/json" -d @examples/node-red/book-keeper-assistant.json
   ```
4. **Variáveis do Node-RED:** em **Menu → Configuration nodes → global → env**, ou como variáveis de sistema do container:

   | Variável | Exemplo |
   | --- | --- |
   | `ASSISTANTS_BASE_URL` | `http://backend:8080/v1` |
   | `ASSISTANTS_API_KEY` | uma das chaves de `AGENT_API_KEYS` |
   | `BOOKKEEPER_ASSISTANT_ID` | o `id` do passo 3 |
   | `BOOKKEEPER_PGHOST` | `postgres` (na rede do compose) |
   | `BOOKKEEPER_PGPORT` | `5432` |
   | `BOOKKEEPER_PGDATABASE` | `agent-server` |

   O Node-RED precisa estar na rede do compose, para alcançar `backend` e `postgres`:
   ```bash
   docker network connect ai-agent-server_default node-red
   ```
5. **Importe e preencha as credenciais:** importe `book-keeper-flow.json` (**Menu → Import**) e preencha:
   - em **Agent Server PostgreSQL**: usuário e senha do banco (no compose, `agent-server` e o `POSTGRES_PASSWORD`, padrão `agent-server`);
   - em **BookKeeperBot**: o token de um bot criado no @BotFather, e o nome dele.

   As credenciais ficam no arquivo de credenciais do Node-RED, nunca no JSON do flow. Use um bot só para o Book Keeper: o telegrambot recusa o mesmo token em dois nós de configuração, e dois flows no mesmo bot responderiam às mesmas mensagens.
6. Faça o deploy e mande "quero um livro de fantasia" para o bot.

**Segurança:**
- **Quem fala com o bot:** por padrão, qualquer pessoa que encontre o bot pode usá-lo. Para restringir, preencha `usernames` ou `chatids` no nó do bot.
- **Banco:** o usuário do banco só precisa de `SELECT`, `INSERT` e `UPDATE` no schema `bookkeeper`. Fora do uso local, crie um usuário próprio para o Node-RED em vez de usar o do Agent Server.
