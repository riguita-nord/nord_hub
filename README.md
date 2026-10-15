# Nord Hub

Espaço de trabalho para organizar projetos, tarefas e contexto de desenvolvimento.

## Executar localmente

Requer Docker Compose. Copia `.env.example` para `.env` e define uma password de base de dados e uma chave JWT aleatórias (mínimo de 32 bytes):

```bash
cp .env.example .env
docker compose up --build
```

Abre `http://localhost:8080`. O Flyway cria o esquema PostgreSQL no primeiro arranque. Para desenvolvimento apenas do frontend, também podes servir a pasta com `python3 -m http.server 8080`; sem a API, a aplicação abre em modo de demonstração e guarda os dados no navegador.

## O que esta fase inclui

- Interface responsiva em português, com projetos, tarefas, calendário, planeador, ideias, notas, foco e Gateway.
- Backend modular em Java 21 / Spring Boot e PostgreSQL, com migração inicial.
- Registo e login por email/password, tokens JWT, hash BCrypt e espaços de trabalho isolados.
- Permissões Owner, Editor e Viewer aplicadas na API; convites de utilização única, válidos por sete dias, com link partilhado manualmente.
- Persistência API dos módulos, estado de manutenção, catálogo de atualizações, histórico administrativo e limites de pedidos por IP.
- Docker Compose, proxy same-origin e workflow GitHub Actions para compilar e executar testes com Java 21.

## Limites conhecidos

- O convite cria um link para partilha manual; não envia email. Não há verificação de email, recuperação de password, OAuth ou renovação de token.
- O limite de pedidos fica em memória por instância e não substitui um rate limiter distribuído.
- As verificações de serviços são básicas; não existe instalador Ubuntu, backup/restauro, atualização/rollback, sincronização GitHub, planeador IA ou integração Nord SaaS.
- A interface continua em português; tradução inglesa e QA visual de browser ainda estão pendentes.
- A fase 2 está em PR de revisão. O build Java e os testes devem ser confirmados pelo CI GitHub; o ambiente local atual não tem Maven nem JDK 21.

Consulta [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) para a checklist e verificação.
