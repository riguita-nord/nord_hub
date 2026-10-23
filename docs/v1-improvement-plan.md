# Nord Hub — Revisão incremental V1

## Base de trabalho

Esta linha de melhoria parte de `backend/phase-3-4-production`. A branch `main` contém apenas um README e **não** deve ser usada como base para comparar as alterações funcionais desta revisão.

## Alterações da primeira passagem

- Recuperação de falhas de escrita no frontend: após um POST, PATCH ou DELETE rejeitado, a interface tenta sincronizar novamente a coleção com a API; se a leitura também falhar, restaura a coleção anterior.
- Mantém-se o modo de demonstração separado do modo autenticado.
- Não foram alterados contratos REST, migrations, nem o instalador nesta passagem.

## Aspetos a validar antes de produção

1. Executar `node --check src/app.js` e os testes Java existentes.
2. Testar criação, edição e remoção com respostas 4xx/5xx e falha de rede.
3. Verificar **escritas concorrentes** na mesma coleção: a recuperação por coleção evita estados falsamente confirmados, mas ainda não implementa uma fila por registo nem resolução de conflitos.
4. Testar autenticação, isolamento entre workspaces, instalação limpa, atualização e restauro num Ubuntu 24.04 real.
5. Concluir QA responsivo, acessibilidade e internacionalização PT/EN.

## Próximos blocos técnicos

- Extrair o código da store e comunicação de API de `src/app.js` para módulos próprios sem alterar o comportamento de produção.
- Introduzir testes automatizados específicos para mutações e conflitos.
- Consolidar componentes repetidos do frontend e as tokens de estilo.
- Rever a política de sessões, recuperação de conta, TLS, observabilidade e administração.
- Só depois evoluir a experiência visual de Projetos, Tarefas e Centro de Trabalho.

A checklist descreve trabalho futuro, não funcionalidades já validadas.
