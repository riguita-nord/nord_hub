# Nord Hub — estado de implementação

## Fase 1 — Frontend

- [x] Estrutura responsiva, navegação e temas claro/escuro.
- [x] Centro de Trabalho, projetos, tarefas, calendário, planeador, ideias, notas e modo de foco.
- [x] Releases de projeto, equipa, notificações e vistas de Gateway.
- [x] Modo de demonstração local e fronteira de serviço separada da interface.
- [ ] Tradução completa português/inglês.
- [ ] QA visual e de interação em browser (Chromium não está disponível neste ambiente).

## Fase 2 — Backend e Gateway

- [x] Java 21 + Spring Boot, PostgreSQL, esquema Flyway e configuração Docker Compose.
- [x] Registo/login, JWT, BCrypt e identidade atual.
- [x] Dados de projetos, tarefas, ideias, notas, eventos, releases e notificações persistidos por API.
- [x] Isolamento por workspace e papéis Owner/Editor/Viewer verificados no servidor.
- [x] Convites de uso único, expirados após sete dias, vinculados ao email e com partilha manual do link.
- [x] Gateway com manutenção normal/só leitura/total, atualizações, estado de serviços e auditoria administrativa.
- [x] Limites de pedidos por IP e CI Java 21 com testes de integração.
- [ ] Confirmar build/testes no GitHub Actions; execução local indisponível (sem Maven/JDK 21).
- [ ] QA visual/fluxos autenticados em browser.

## Fase 3 — Instalador e integrações

- [x] Instalador idempotente para Ubuntu 24.04, geração de segredos e preservação de `.env` existente.
- [x] Assistente de primeira configuração para criar o administrador e workspace.
- [x] Scripts de backup, restauro confirmado, atualização com cópia prévia e diagnóstico/reparação.
- [x] GitHub: associação verificada de repositórios, atividade de commits/PRs e webhooks com HMAC e deduplicação.
- [x] Referências explícitas de tarefas nos commits/PRs; eventos criam notificações e nunca concluem tarefas.
- [x] Planeador IA opcional com proposta revista antes de criar eventos.
- [x] Adaptador Nord SaaS Lite/Forge de leitura com os endpoints verificados de health, status, projetos e releases; acesso limitado ao administrador global do Nord Hub.

## Fase 4 — Preparação para produção

- [x] Testes de integração para APIs, isolamento de workspaces, setup inicial e validação de referências.
- [x] Smoke tests do instalador, sintaxe dos scripts e checks JavaScript integrados no CI.
- [x] Documentação de instalação, variáveis, integrações, operação e recuperação.
- [ ] Executar build e testes Java 21 no GitHub Actions.
- [ ] Exercitar instalação nova, atualização e restauro numa VM Ubuntu 24.04 com Docker/PostgreSQL.
- [ ] QA visual/interação em browser e completar tradução inglesa.
- [ ] Revisão de segurança operacional antes de exposição pública (TLS, segredo/rotação de tokens, limites distribuídos e métricas).
- [ ] A sincronização Nord SaaS usa sessão Store ativa, que o operador deve configurar e renovar; não há API credential/service account para integração duradoura.

## Verificação disponível

- `node --check src/app.js` — passou.
- `node --check src/api.js` — passou.
- Testes de integração Java adicionados para criação/persistência, isolamento entre workspaces e validação de referências de tarefas.
- `mvn -f backend/pom.xml verify` — não executado localmente: Maven e JDK 21 não estão instalados. O workflow GitHub Actions executa este comando com Temurin 21.
- Teste visual em browser — não executado: falta o binário Chromium no ambiente.
