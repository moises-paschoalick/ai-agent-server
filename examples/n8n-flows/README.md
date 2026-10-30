# Flows do n8n

Importe no n8n em **Workflows → Import from File**.

## book-keeper.json

Atendente de livraria pelo WhatsApp, usando a API de Assistants:
1. Um webhook recebe a mensagem da Zapster API.
2. O fluxo procura o usuário no Baserow (ou o cria) e reaproveita a thread dele, ou cria uma nova e a guarda.
3. Se houver uma run ativa na thread, ela é cancelada. Depois o fluxo adiciona a mensagem, cria a run e consulta o status a cada 4 s.
4. Quando a run chega em `requires_action`, a tool `get_books` busca os produtos no Baserow pela categoria e devolve o resultado com `submit_tool_outputs`.
5. Com a run `completed`, a última resposta vai de volta pelo WhatsApp.

**Antes de usar, preencha:**
- `<ASSISTANT_ID>`, no nó **Run Assistant**;
- `<ZAPSTER_API_TOKEN>` e `<ZAPSTER_INSTANCE_ID>`, no nó **Envia Whatsapp** (de preferência por uma credencial do n8n, e não escritos no nó);
- as credenciais **OpenAi account** e **Baserow account**;
- os ids de banco, tabela e campos do Baserow, que são os da instalação de origem.

O `pinData` do **Webhook** é um exemplo fictício no formato da Zapster, para testar sem WhatsApp.

**Usar com o Agent Server em vez da OpenAI:**
1. Nos nós HTTP, troque `https://api.openai.com/v1` pela URL do Agent Server com `/v1` (por exemplo, `http://backend:8080/v1` na rede do Docker).
2. Troque a credencial por uma chave de `AGENT_API_KEYS`.
3. Troque o `assistant_id` pelo id de um assistant cadastrado no Agent Server (`POST /v1/assistants`).

O cabeçalho `OpenAI-Beta` pode ficar: o servidor ignora cabeçalhos que não conhece.
