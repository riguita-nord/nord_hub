# Nord Hub

Espaço de trabalho para programadores organizarem projetos, tarefas, calendário, ideias e pontos de retoma. A aplicação usa Java 21/Spring Boot, PostgreSQL e uma interface HTML/CSS/JavaScript servida pelo Nginx.

## Instalação em Ubuntu 24.04

Na pasta do repositório, executa como root:

```bash
sudo ./install.sh
```

O instalador verifica o sistema, instala Docker Engine e Compose quando necessário, cria segredos aleatórios sem substituir uma configuração existente, inicia os serviços e testa a API. Abre `http://<servidor>:8080` e conclui o assistente para criar o primeiro administrador. O instalador mostra o token de configuração apenas no terminal. Após criar a conta, remove `NORD_HUB_SETUP_TOKEN` de `.env` e recria a API:

```bash
sudo docker compose up -d --force-recreate api
```

O instalador é repetível e preserva `.env`, volumes e dados existentes. `sudo ./install.sh --dry-run` valida o host e mostra as ações sem as executar. Para usar Docker já instalado, passa `--skip-packages`.

### Desenvolvimento local

Com Java 21, Maven e PostgreSQL configurados, executa `mvn -f backend/pom.xml spring-boot:run`. Para apenas pré-visualizar a interface sem backend, executa `python3 -m http.server 8080`; essa demonstração usa `localStorage` e não é persistência de produção.

## Backups, restauro, atualização e reparação

```bash
sudo ./scripts/backup.sh
sudo ./scripts/restore.sh backups/nord_hub-<data>.dump --confirm
sudo ./scripts/update.sh
sudo ./scripts/repair.sh
sudo ./scripts/repair.sh --restart
```

Os backups PostgreSQL são em formato customizado, validados com `pg_restore --list` e guardados com permissões restritas. O restauro substitui a base atual: cria primeiro um backup atual e confirma que escolheste o ficheiro correto. O script de atualização cria backup antes de aplicar migrations e tenta repor a imagem anterior se a API não ficar saudável. Uma mudança de esquema incompatível pode exigir restauro manual do dump; testa atualizações em staging antes de produção.

## Integrações opcionais

As chaves são configuradas apenas no `.env` do servidor; a interface nunca as recebe nem as mostra.

- **GitHub:** define `NORD_HUB_GITHUB_TOKEN` com um Fine-grained PAT e permissões mínimas de leitura para os repositórios necessários. Owners/Editors podem associar repositórios ao workspace; a interface consulta commits e pull requests. As referências `[nord-task:<UUID>]` em mensagens são mostradas como associação informativa; nunca concluem tarefas automaticamente.
- **Webhooks GitHub:** define `NORD_HUB_GITHUB_WEBHOOK_SECRET` com pelo menos 32 bytes. URL: `https://<domínio>/api/v1/integrations/github/webhook`. Subscreve `push` e `pull_request` com content type JSON. A assinatura HMAC é validada, o delivery ID é deduplicado e as referências de tarefas existentes geram notificações no workspace.
- **Planeador IA:** define `NORD_HUB_AI_API_KEY`, `NORD_HUB_AI_BASE_URL` e `NORD_HUB_AI_MODEL` para um endpoint compatível com OpenAI Chat Completions. O contexto enviado contém apenas tarefas abertas, prioridades, prazos e estimativas. Cada plano é uma proposta; só é escrito no calendário depois de aprovação explícita.
- **Nord SaaS Lite / Forge:** foi verificado no repositório `riguita-nord/nord-saas-lite` o contrato de leitura `GET /health/ready`, `GET /api/v1/status`, `GET /api/v1/forge/projects` e `GET /api/v1/forge/releases`. A integração é apenas de leitura e só pode ser consultada pelo administrador global do Nord Hub. Configura `NORD_HUB_NORD_SAAS_URL` (origem HTTPS) e `NORD_HUB_NORD_SAAS_SESSION_TOKEN` no servidor; a sessão tem de pertencer a uma conta Nord SaaS com perfil Store ativo e acesso Forge. O token usa o header existente `X-Nord-Session`, nunca é enviado ao browser e não é guardado na base de dados. A sincronização mostra versão/saúde, estados dos projetos e releases; não altera nenhum dos dois sistemas. Sessões expiradas têm de ser renovadas pelo operador.

Depois de alterar variáveis de ambiente: `sudo docker compose up -d --force-recreate api`.

## Segurança e operação

- Tokens, passwords e chaves ficam no servidor; define um domínio HTTPS num reverse proxy antes de expor a aplicação à Internet.
- A primeira conta de administrador é protegida por token temporário e email pré-configurado. O bootstrap fica bloqueado depois de existir administrador.
- JWT tem validade curta. Ainda não existe refresh token, recuperação de password, verificação de email ou OAuth por utilizador.
- O rate limit do Gateway é por instância/IP e não substitui uma camada distribuída para ambientes com várias réplicas.
- As migrations são versionadas no Flyway. Mantém backups fora do servidor e ensaia o restauro periodicamente.

Ver [docs/operations.md](docs/operations.md) e [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) para procedimentos e limites conhecidos.
