# Java

O `/v1` do Agent Server é compatível com a API de Assistants da OpenAI, então o **SDK oficial da OpenAI para Java** (`com.openai:openai-java`) funciona sem mudança de código. Basta apontar a base URL para o servidor e usar uma das chaves de `AGENT_API_KEYS`:

```java
OpenAIClient client = OpenAIOkHttpClient.builder()
        .baseUrl("http://localhost:8080/v1")   // em vez de https://api.openai.com/v1
        .apiKey(System.getenv("AGENT_API_KEY"))
        .build();
```

O resto do código é o mesmo que falaria com a OpenAI: `client.beta().assistants()`, `threads()`, `runs()` e `messages()`.

## AssistantsExample

[`AssistantsExample.java`](src/main/java/examples/AssistantsExample.java) faz o ciclo completo:
1. cria um assistant com a tool `get_order_status`;
2. cria uma thread com a pergunta "What is the status of order A-1001?" e uma run;
3. consulta a run até sair de `queued`/`in_progress`;
4. em `requires_action`, executa a tool no próprio cliente (um mapa de pedidos fictícios) e devolve o resultado com `submitToolOutputs`;
5. com a run `completed`, imprime a última mensagem e os tokens;
6. no fim, remove o assistant que criou.

Para rodar, a partir da raiz do repositório, com o Agent Server no ar (usa o Gradle Wrapper do backend; precisa de Java 21):

```bash
AGENT_API_KEY=<uma das AGENT_API_KEYS> backend/gradlew -p examples/java run
```

Saída esperada:

```
Assistant asst_... at http://localhost:8080/v1
Tool get_order_status {"order_id":"A-1001"} -> shipped yesterday, arrives tomorrow
Answer: Order A-1001 has shipped yesterday and is expected to arrive tomorrow.
Tokens: 1438
```

Cada configuração é procurada nesta ordem: variável de ambiente, arquivo `.env` em `examples/java/` (formato `CHAVE=valor`, fora do git), [`application.properties`](src/main/resources/application.properties) e, por fim, o padrão da tabela. Com a chave no `.env`, basta `backend/gradlew -p examples/java run`, ou rodar `AssistantsExample` direto pelo IntelliJ (o diretório de trabalho precisa ser `examples/java`).

| Variável | Padrão | Uso |
| --- | --- | --- |
| `AGENT_BASE_URL` | `http://localhost:8080/v1` | base do `/v1` do Agent Server |
| `AGENT_API_KEY` | (obrigatória) | uma das `AGENT_API_KEYS` |
| `AGENT_MODEL` | `openai:gpt-5.4-mini` | modelo do assistant, no formato `provedor:modelo` |

Testado com `openai-java` 4.80.0 contra o Agent Server local.

**O que não muda nem precisa de ajuste:**
- cabeçalhos que o SDK manda e o servidor não usa, como `OpenAI-Beta`, são ignorados;
- os ids (`asst_`, `thread_`, `run_`, `call_`) e os objetos seguem o formato da OpenAI;
- `function.arguments` chega como string JSON, como na OpenAI.

**Limites do Agent Server no `/v1`:**
- só tools `function`;
- sem streaming nas runs (`stream: true` responde 400);
- sem run steps.

O streaming por SSE existe no contrato síncrono (`/chat`); ver [Streaming](../../README.md#streaming).
