# Publicação e autenticação do GeoQuest

O projeto usa Java 25, Spring Boot 3.5.16, Spring Security 6.5.11 e PostgreSQL padrão no Neon.
O frontend e a API continuam juntos no Docker. O protocolo OAuth2/OIDC é executado pelo Spring;
as sessões persistentes do jogo continuam nas tabelas existentes.

## Ambiente publicado — 2 de novembro de 2026

- Aplicação: [GeoQuest na Vercel](https://geoquest-raibrs.vercel.app/).
- Projeto: [geoquest-migration](https://vercel.com/raibrs/geoquest-migration), equipe `raibrs`, plano Hobby.
- A branch `main` do repositório `RaiBrs/GeoQuest` dispara a publicação automática.
- As migrações 001 a 004 estão aplicadas no banco Neon existente.
- Google e GitHub completaram login real no domínio da Vercel. O ranking e os três modos também foram verificados.
- O serviço `geoquest-raibrs` do Render foi excluído após a validação; não está disponível para rollback.
- Consulte o [registro da migração](VERCEL_MIGRATION.md) e o [índice da documentação](README.md).

## 1. Banco de dados

Para uma instalação nova, aplique os arquivos nesta ordem:

1. `database/001_accounts.sql`
2. `database/002_oauth_profile.sql`
3. `database/003_shared_rounds.sql`
4. `database/004_shared_oauth_sessions.sql`
5. `database/005_timed_rankings.sql`

Em um banco existente com a migração 001, aplique apenas a 002. Ela adiciona email/avatar opcionais ao
perfil e o provedor à sessão, sem excluir perfis, identidades, sessões ou resultados. Para sessões antigas,
o provedor é preenchido somente quando o perfil tem uma identidade inequívoca. As migrações 003 e 004
adicionam partidas compartilhadas e sessões temporárias OAuth. Aplique as migrações ausentes em ordem,
sem recriar o banco. Teste em ambiente isolado antes de alterar o banco publicado.

A migração 005 remove somente resultados de `CAPITALS` e `SURVIVAL`. O trigger ignora gravações
desses modos por versões antigas durante a atualização, sem provocar falha ao concluir uma partida.
Resultados `TIMED`, perfis e sessões são preservados. Normal continua com dez perguntas; sobrevivência
passa a usar o catálogo elegível completo nos novos jogos criados após publicar o código atualizado.
Partidas já iniciadas mantêm as perguntas com que foram criadas.

Validação da migração 005 em 3 de novembro de 2026: os testes locais passaram e a cópia Neon
`timed-ranking-migration-check-20261103` preservou os sete resultados cronometrados com o mesmo
checksum. Essa branch de teste expira em 4 de novembro de 2026 às 12h de São Paulo.
Após aprovação explícita, a migração 005 foi aplicada no banco publicado em 3 de novembro de 2026:
foram removidos quatro resultados de normal e doze de sobrevivência. Os sete cronometrados
mantiveram o mesmo checksum, e o trigger `player_results_timed_only` está ativo.
A nova interface e as regras de sobrevivência entram em vigor com a publicação do código atualizado.

Forneça a conexão JDBC, usuário e senha separadamente nas variáveis do servidor:

    JDBC_DATABASE_URL=jdbc:postgresql://YOUR-ENDPOINT.neon.tech/geoquest?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory
    DATABASE_USER=your-database-role
    DATABASE_PASSWORD=your-database-password

A configuração verifica o certificado e o hostname usando as autoridades confiáveis da JVM. Não coloque
credenciais na URL do frontend, em commits ou logs. Use uma role dedicada com acesso às tabelas do jogo.
O lock usado para concorrência de login é transacional e funciona com a conexão pooled do Neon.

## 2. Cadastrar os aplicativos OAuth

Defina `APP_URL` como a origem real, sem caminho. O padrão local é `http://localhost:8080`; `HOST` e
`PORT` podem alterá-lo. HTTP é permitido somente em localhost/127.0.0.1. Na publicação use HTTPS.

| Provedor | Desenvolvimento | Publicação neste serviço |
|---|---|---|
| Google | `http://localhost:8080/login/oauth2/code/google` | `https://geoquest-raibrs.vercel.app/login/oauth2/code/google` |
| GitHub | `http://localhost:8080/login/oauth2/code/github` | `https://geoquest-raibrs.vercel.app/login/oauth2/code/github` |

**Google:** abra https://console.cloud.google.com/auth/overview, selecione/crie um projeto e configure
Branding/Audience do Google Auth Platform. Em Clients crie um cliente do tipo Web application. Cadastre
as URIs acima em Authorized redirect URIs e copie Client ID/Client Secret para as variáveis do servidor.
O aplicativo solicita `openid profile email`. Se o projeto estiver em Testing, configure os usuários de teste.

**GitHub:** abra https://github.com/settings/developers → OAuth Apps → New OAuth App. Use o endereço do
jogo como Homepage URL e a URI específica de GitHub como Authorization callback URL. Gere o Client Secret
e guarde os dois valores no servidor. Para localhost, prefira outro OAuth App. O escopo `read:user` permite
ler o perfil; um email privado pode não ser fornecido. O sistema aceita email ausente e não pede acesso a repositórios.

As antigas rotas `/auth/login` e `/auth/callback` não são usadas nesta versão. Atualize os callbacks externos
antes de habilitar as contas após esta migração.

## 3. Variáveis necessárias

| Variável | Finalidade |
|---|---|
| `API_KEY` | Catálogo REST Countries, independente da autenticação |
| `ACCOUNTS_ENABLED` | `false` para visitantes; `true` para habilitar as contas |
| `SHARED_STATE_ENABLED` | `true` na Vercel; usa PostgreSQL para partidas e sessões temporárias OAuth |
| `APP_URL` | Origem exata usada nos callbacks |
| `HOST`, `PORT` | Na Vercel: `0.0.0.0` e `8080`, respectivamente |
| `JDBC_DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Persistência PostgreSQL |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Cliente OAuth Google |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | OAuth App GitHub |

Configure pelo menos um par completo de provedor. O outro par pode ficar sem configuração; apenas os
provedores configurados aparecem no frontend. Nunca compartilhe segredos pelo chat. Para desenvolvimento,
o `WebApp` ainda lê `.env` ignorado pelo Git; variáveis de processo/IDE têm prioridade sobre esse arquivo.

## 4. Executar e publicar

Localmente, na pasta de `pom.xml`:

    mvn spring-boot:run

Ou gere o executável:

    mvn verify
    java -jar target/Geo-Quest-1.0-SNAPSHOT.jar

Docker:

    docker build -t geoquest .
    docker run --rm --name geoquest -p 127.0.0.1:8080:8080 --mount "type=bind,source=${PWD}\.env,target=/app/.env,readonly" geoquest

O Docker constrói e testa o projeto e executa o JAR Spring Boot como usuário sem privilégios. `.env` e
artefatos locais ficam fora da imagem. O comando monta `.env` somente para leitura e permite que o Java
interprete aspas. Se usar `--env-file`, use outro arquivo sem aspas: o Docker as mantém no valor.
O container escuta em `0.0.0.0:$PORT`. Com `SHARED_STATE_ENABLED=false`, mantenha uma única instância:
partidas e logins OAuth em andamento ficam em memória e são perdidos no reinício. Com a opção `true`,
o estado fica no PostgreSQL e pode ser recuperado por outra instância, respeitando a expiração.
Isso não significa que o frontend retome automaticamente a tela de uma partida após recarregar a página.
Para falhas do Docker Desktop, consulte [DOCKER_TROUBLESHOOTING.md](DOCKER_TROUBLESHOOTING.md).

Na Vercel, `vercel.json` seleciona o framework `container`, que usa o `Dockerfile` da raiz. Frontend e API
continuam juntos no Java. Antes do primeiro deploy:

1. Aplique as cinco migrações no Neon.
2. No projeto `geoquest-migration`, abra **Environment Variables** e selecione **Production**.
3. Cadastre como **Secrets**: `API_KEY`, `JDBC_DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`,
   `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`.
4. Cadastre como **Config**: `ACCOUNTS_ENABLED=true`, `SHARED_STATE_ENABLED=true`, `HOST=0.0.0.0`,
   `PORT=8080` e `APP_URL=https://geoquest-raibrs.vercel.app`.
5. Configure os callbacks exatos da tabela acima nos provedores.
6. Envie as alterações revisadas à `main`, acompanhe **Deployments** até **Ready** e teste o domínio estável.

Mudanças em variáveis exigem um novo deploy para entrar em vigor. Não conecte previews ao banco publicado:
use uma branch/banco de teste e clientes OAuth separados. A página `/` permite conferir a disponibilidade.
O arquivo legado `render.yaml` não é usado pela Vercel e não representa infraestrutura ativa.

## 5. Testes manuais de autenticação

1. Acesse `/` sem conta e conclua uma rodada de visitante.
2. Sem login, `/api/me` deve responder 401 em JSON. `/api/account` permanece público.
3. Visite `/oauth2/authorization/google`; conclua o consentimento e confirme o retorno ao início autenticado.
4. Visite `/api/me`: verifique nome, email quando disponível, `provider`, `providerId` e `avatarUrl`.
5. Saia da conta e repita com `/oauth2/authorization/github`, incluindo um perfil sem email público.
6. Conclua uma rodada autenticada e consulte o ranking correspondente. Reenviar a última resposta
   não deve duplicar o resultado no banco. Não envie pontuações prontas pelo cliente.
7. Use Sair da conta; `/api/me` deve voltar a 401, inclusive ao reutilizar o cookie revogado.
8. Para testar exclusão, use uma conta de teste e login recente. Confirme com DELETE e verifique que
   perfil, identidades, sessões e resultados foram removidos. As partidas desse jogador também devem acabar.

Google e GitHub tiveram login e retorno reais validados na Vercel em 2 de novembro de 2026.
Repita os fluxos após alterações de autenticação. A exclusão foi coberta por testes automatizados;
não foi excluída uma conta real de produção para validar a migração.

## Idioma do navegador

O seletor salva `EN` ou `PT_BR` em `localStorage`, na chave `geoquest.language`. A inicialização
restaura a escolha após recarga, nova aba e retorno do login no mesmo domínio e navegador.
Isso não depende da conta e não sincroniza dispositivos nem origens diferentes. Sem preferência válida,
o padrão continua sendo inglês. Se o armazenamento estiver bloqueado, a troca funciona na página atual,
mas a preferência pode não persistir. Durante partidas e resultados, o idioma permanece fixo.

## CSRF, sessões e APIs privadas

`GET /api/csrf` devolve `token` e `headerName`. O navegador deve manter o cookie CSRF e enviar o token no
header `X-XSRF-TOKEN` de todo POST, inclusive nas partidas de visitantes. O módulo `http-client.js` faz isso
para contas e jogo; uma resposta 403 de CSRF pede atualização da página. CORS continua restrito à mesma origem.

`/api/me`, `/api/logout` e `/api/account/delete` exigem autenticação. Os endpoints do jogo aceitam visitantes,
mas o proprietário de uma rodada autenticada é conferido pelo servidor. Pontuações do ranking são calculadas
no Java e inseridas de forma idempotente. Cada API autenticada consulta a sessão uma vez; os controllers usam
o usuário já resolvido no `SecurityContext`, sem outra consulta de identidade.

O `JSESSIONID` temporário é usado pelo Spring para state/nonce/PKCE do OAuth. Após o login ele é invalidado.
A sessão persistente de autenticação do jogador é `geoquest_session`, aleatória, HttpOnly, SameSite=Lax e Secure em HTTPS, com validade
de sete dias. Somente seu hash SHA-256 fica no banco. Não há JWT, Basic Auth ou segunda base de usuários.
Tokens do provedor são usados temporariamente pelo Spring e descartados ao concluir o login; não vão para
as tabelas do jogo. Com estado compartilhado, o `JSESSIONID` usa as tabelas `geoquest_http_session` e
`geoquest_http_session_attributes`, com expiração por inatividade de dez minutos. Exclusão requer login
nos últimos dez minutos e confirmação DELETE. A exclusão não apaga
a conta Google/GitHub nem revoga automaticamente as permissões externas; isso pode ser feito nos provedores.

## Testes automatizados

    mvn test
    node --test frontend/tests/*.test.js

Os testes normais não exigem credenciais de produção. Para a suíte PostgreSQL, use um banco descartável chamado
`geoquest_test`, role `geoquest_test`, senha `test-only-password`, e defina `TEST_DATABASE_URL` com sua URL JDBC.
A suíte valida o nome dedicado antes de limpar dados de teste. `TEST_DATABASE_USER` e
`TEST_DATABASE_PASSWORD` permitem sobrescrever as credenciais de teste. Não use a URL do Neon publicado.

Validação registrada em 2 de novembro de 2026: 140 testes Java passaram no banco local descartável
durante a migração; após a correção de idioma, os 14 testes frontend passaram. Esta atualização de
documentação não representa uma nova execução da suíte Java.

## Referências

- Spring Boot/Java: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- OAuth2 Login: https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/login/advanced.html
- CSRF: https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html
- Google: https://developers.google.com/identity/protocols/oauth2/web-server
- GitHub: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app
- Vercel Container: https://vercel.com/docs/functions/runtimes/container
- Neon/JDBC: https://neon.com/docs/guides/java
