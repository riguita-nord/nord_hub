# Nord Hub — Instalação no servidor de testes (Ubuntu 24.04)

> **Pré-produção.** Não apontar diretamente um domínio público sem TLS, firewall e revisão operacional. A versão nesta branch está em validação.

## Nova instalação

Num servidor Ubuntu 24.04 com acesso sudo, clona a branch de testes:

```bash
sudo apt-get update
sudo apt-get install -y git
git clone --branch improvement/v1-foundation-hardening https://github.com/riguita-nord/nord_hub.git
cd nord_hub
sudo bash ./install.sh --dry-run
sudo bash ./install.sh
```

O instalador pede o email do administrador, instala Docker/Compose quando necessário, cria credenciais aleatórias em `.env`, inicia a stack e testa `/api/v1/health`. Guarda **em privado** o token de setup apresentado no terminal; usa-o no assistente inicial. Nunca o coloques em logs públicos, issues ou chats.

### Aceder ao painel com SSH (opção recomendada)

A interface fica limitada ao endereço local do servidor por defeito. No computador pessoal:

```bash
ssh -L 8080:127.0.0.1:8080 UTILIZADOR@IP_DO_SERVIDOR
```

Abre `http://127.0.0.1:8080` no teu browser, termina o setup e confirma a criação do workspace.

### Se precisares de acesso externo

Configura primeiro reverse proxy HTTPS e firewall. A variável `NORD_HUB_BIND_ADDRESS` pode ser ajustada no `.env`; `0.0.0.0` expõe HTTP na rede e **não** é adequado para acesso público sem proteção adicional. Um reverse proxy no próprio host pode manter a ligação local ao Nord Hub sem expor a porta 8080.

## Após criar o primeiro administrador

Desativa o token inicial para o processo API, sem alterar os outros segredos:

```bash
sudo sed -i '/^NORD_HUB_SETUP_TOKEN=/d' .env
sudo docker compose up -d --force-recreate api
```

Reinstalações com `.env` existente **não** recriam automaticamente um token inicial. A configuração já concluída fica protegida pelo estado persistido na base de dados.

## Diagnóstico e operação

```bash
sudo docker compose ps
sudo docker compose logs --tail=150 api db web
curl --fail --show-error http://127.0.0.1:8080/api/v1/health
sudo bash ./scripts/repair.sh
```

Para testar uma atualização, valida primeiro um backup, segue `docs/operations.md` e usa `sudo bash ./scripts/update.sh` apenas numa instalação de testes. Nunca uses `docker compose down -v` a menos que pretendas apagar os volumes da base de dados.

## Checklist de aceitação

- [ ] `docker compose ps` indica os serviços saudáveis
- [ ] `/api/v1/health` responde sem erros
- [ ] O setup inicial cria administrador e workspace
- [ ] Login funciona após a remoção do token
- [ ] Criar, editar e eliminar projeto e tarefa persiste após recarregar
- [ ] Um utilizador de outro workspace não acede aos dados
- [ ] Testar falha de rede durante uma gravação e confirmar rollback visual
- [ ] Backup e restauro exercitados numa cópia de teste
- [ ] Rever logs, HTTPS, firewall e permissões antes de produção

## Limitações ainda conhecidas

A camada frontend ainda necessita de testes visuais completos, traduções e proteção adicional de operações concorrentes. Integrações com Nord-SaaS, GitHub e IA são opcionais e requerem configurações próprias. **Este guia não certifica a aplicação para produção.**
