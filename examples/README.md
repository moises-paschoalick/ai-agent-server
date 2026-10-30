# Exemplos

Exemplos de clientes do Agent Server, um por linguagem ou ferramenta. Eles usam as duas APIs do servidor:
- **`/v1`:** compatível com a API de Assistants da OpenAI (threads, messages, runs).
- **Contrato síncrono:** `/chat`, `/tools/result` e o streaming por SSE (ver [Streaming](../README.md#streaming)).

| Pasta | Conteúdo |
| --- | --- |
| [`n8n-flows/`](n8n-flows/) | flows do n8n para importar |
| [`node-red/`](node-red/) | flows do Node-RED |
| [`java/`](java/) | clientes em Java |

**Regras para os exemplos:**
- Nenhum exemplo traz chave, token, telefone ou outro dado real.
- Valores que dependem de cada instalação ficam como placeholder: `<ASSISTANT_ID>`, `<AGENT_API_KEY>`, `<BASE_URL>`.
- Flows exportados (n8n, Node-RED) passam por limpeza antes de entrar aqui: sai o `pinData` com dados de execuções reais, saem os ids de credenciais e saem os tokens escritos direto nos nós.
