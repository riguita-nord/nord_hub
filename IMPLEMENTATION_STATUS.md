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

## Fases futuras

- [ ] Entrega de convites por email, verificação de email, recuperação de password e renovação de sessão.
- [ ] Limitador distribuído, métricas operacionais aprofundadas e revisão de segurança de produção.
- [ ] Instalador Ubuntu 24.04, configuração inicial, backup/restauro, atualização e rollback.
- [ ] Webhooks e mapeamento de repositórios, commits e PRs GitHub.
- [ ] Planeador IA opcional e integração Nord SaaS após revisão do contrato de API.
- [ ] Localização inglesa e documentação de operação/instalação.

## Verificação disponível

- `node --check src/app.js` — passou.
- `node --check src/api.js` — passou.
- Testes de integração Java adicionados para criação/persistência, isolamento entre workspaces e validação de referências de tarefas.
- `mvn -f backend/pom.xml verify` — não executado localmente: Maven e JDK 21 não estão instalados. O workflow GitHub Actions executa este comando com Temurin 21.
- Teste visual em browser — não executado: falta o binário Chromium no ambiente.
