# Nord Hub — operação

## Configuração inicial

1. Instala com `sudo ./install.sh` em Ubuntu 24.04.
2. Introduz o email administrador definido no assistente de configuração. A palavra-passe deve ter 12 ou mais caracteres.
3. Guarda o token apresentado no terminal e conclui o bootstrap na interface.
4. Remove `NORD_HUB_SETUP_TOKEN` de `.env` e recria `api`.
5. Configura HTTPS no reverse proxy de rede antes de permitir acesso externo.

Não publiques `.env`. O `.gitignore` exclui esse ficheiro; as cópias de backup devem ficar fora do repositório.

## Serviços e verificações

- Interface: porta `NORD_HUB_WEB_PORT` (8080 por omissão).
- API: só é exposta ao Nginx interno, porta 8081.
- PostgreSQL: rede Docker interna, volume persistente `nord_hub_postgres`.
- Health: `/api/v1/health`; detalhes do Actuator não são expostos pelo Nginx.
- Logs: `sudo docker compose logs --tail=200 api db web`.

## Atualizações e rollback

`./scripts/update.sh` guarda um dump PostgreSQL antes do build, aplica migrations Flyway e aguarda health. Se o serviço falhar, tenta repor a imagem anterior; guarda o dump para recuperação manual caso a alteração de esquema não seja compatível com o código antigo. Testa a versão nova e o restauro numa cópia de staging. Não apagues o volume PostgreSQL para reparar a aplicação.

Para restaurar: faz primeiro um backup atual, usa `./scripts/restore.sh <ficheiro.dump> --confirm` e confirma `/api/v1/health`. O restauro substitui dados correntes.

## Integração GitHub

Usa um Fine-grained PAT do lado do servidor, limitado aos repositórios e permissões de leitura necessários. Associa os repositórios no separador Integrações. Os commits e PRs são consultados quando abres a atividade. As associações a tarefas usam `[nord-task:<UUID>]`; não alteram o estado das tarefas.

Para webhooks, configura um segredo de pelo menos 32 bytes no `.env`, cria o webhook em cada repositório e aponta para `/api/v1/integrations/github/webhook`. O servidor verifica `X-Hub-Signature-256`, guarda o delivery ID único e ignora entregas repetidas. Pushes e PRs com referências válidas criam notificações; não concluem tarefas.

## Planeador IA

Define a chave e um endpoint compatível com Chat Completions no `.env`. O endpoint envia apenas a lista de tarefas abertas, projeto associado, prioridade, prazo e estimativa. A IA pode devolver conteúdo incorreto; revê o plano e aprova manualmente antes de criar blocos.

## Nord SaaS Lite / Forge

O contrato foi confirmado em `riguita-nord/nord-saas-lite`: `GET /health/ready`, `GET /api/v1/status`, `GET /api/v1/forge/projects` e `GET /api/v1/forge/releases`. Define `NORD_HUB_NORD_SAAS_URL` com uma origem HTTPS e `NORD_HUB_NORD_SAAS_SESSION_TOKEN` com uma sessão ativa pertencente a um utilizador com perfil Store ativo e acesso Forge. O Nord Hub envia a sessão apenas como `X-Nord-Session` do servidor remoto; apenas administradores globais podem consultar a sincronização. O painel lê a versão e disponibilidade dos serviços, estados de projetos Forge e releases, sem escrever nem copiar alterações para qualquer sistema. Uma sessão expirada deve ser substituída no `.env` e a API recriada.

## Limites operacionais

- O instalador não configura DNS, TLS, firewall cloud nem SMTP.
- Backups locais não protegem contra perda do servidor; replica-os para armazenamento externo seguro.
- Não existe entrega de convites por email, refresh token, recuperação de password ou login OAuth.
- O limitador de pedidos vive em memória e exige um reverse proxy/rate limiter adicional para várias instâncias.
- A integração Nord SaaS Lite é só de leitura e usa uma sessão de utilizador que expira; não há credencial de serviço/refresh token documentado para sincronização duradoura.
