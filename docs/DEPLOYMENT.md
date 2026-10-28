# Publicação e autenticação do GeoQuest

O projeto usa Java 25, Spring Boot 3.5.16, Spring Security 6.5.11 e PostgreSQL padrão no Neon.
O frontend e a API continuam juntos no Docker. O protocolo OAuth2/OIDC é executado pelo Spring;
as sessões persistentes do jogo continuam nas tabelas existentes.

## Situação desta migração

- Endereço da aplicação: https://geoquest-raibrs.onrender.com.
- A implementação Spring usa `feat/spring-security-oauth2` e é integrada em `feat/render-neon-accounts`, a branch acompanhada pelo serviço existente.
- O serviço existente está em https://dashboard.render.com/web/srv-dbh2tcks728c73anvrk0.
- O banco Neon recebeu as migrações 001 e 002. A 002 foi validada primeiro na branch isolada `spring-oauth-migration-check`.
- As credenciais Google/GitHub foram configuradas diretamente no Render. O consentimento e o retorno devem ser conferidos com uma conta real após o deploy.

## 1. Banco de dados

Para uma instalação nova, aplique os arquivos nesta ordem:

1. `database/001_accounts.sql`
2. `database/002_oauth_profile.sql`

Em um banco existente com a migração 001, aplique apenas a 002. Ela adiciona email/avatar opcionais ao
perfil e o provedor à sessão, sem excluir perfis, identidades, sessões ou resultados. Para sessões antigas,
o provedor é preenchido somente quando o perfil tem uma identidade inequívoca. Teste a migração em um
ambiente isolado antes de aplicá-la ao banco publicado. O aplicativo verifica todas essas tabelas na inicialização.

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
| Google | `http://localhost:8080/login/oauth2/code/google` | `https://geoquest-raibrs.onrender.com/login/oauth2/code/google` |
| GitHub | `http://localhost:8080/login/oauth2/code/github` | `https://geoquest-raibrs.onrender.com/login/oauth2/code/github` |

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
| `APP_URL` | Origem exata usada nos callbacks |
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
artefatos locais ficam fora da imagem. O comando local monta o .env somente para leitura e permite que o Java interprete aspas. Se usar --env-file, use outro arquivo sem aspas: o Docker as mantém no valor. O container escuta em `0.0.0.0:$PORT` e continua exigindo uma única
instância: partidas ativas vivem em memória. Reinícios preservam perfis/resultados/sessões no banco,
mas encerram partidas e logins OAuth ainda em andamento.

Para atualizar o Render, envie a branch revisada ao GitHub e selecione-a no serviço (ou integre no branch
que ele acompanha). Configure as variáveis em Environment, aplique as migrações, confirme o plano Free
e habilite `ACCOUNTS_ENABLED=true` somente com credenciais reais. Health check: `/`. Não faça upgrade
para um plano pago para esta etapa. Consulte as cotas e condições atuais dos planos nos painéis dos provedores.

## 5. Testes manuais de autenticação

1. Acesse `/` sem conta e conclua uma rodada de visitante.
2. Sem login, `/api/me` deve responder 401 em JSON. `/api/account` permanece público.
3. Visite `/oauth2/authorization/google`; conclua o consentimento e confirme o retorno ao hub.
4. Visite `/api/me`: verifique nome, email quando disponível, `provider`, `providerId` e `avatarUrl`.
5. Saia da conta e repita com `/oauth2/authorization/github`, incluindo um perfil sem email público.
6. Conclua uma rodada autenticada e consulte o ranking correspondente. Reenviar a última resposta
   não deve duplicar o resultado no banco. Não envie pontuações prontas pelo cliente.
7. Use Sair da conta; `/api/me` deve voltar a 401, inclusive ao reutilizar o cookie revogado.
8. Para testar exclusão, use uma conta de teste e login recente. Confirme com DELETE e verifique que
   perfil, identidades, sessões e resultados foram removidos. As partidas desse jogador também devem acabar.

Nenhum login real é considerado validado até as etapas com credenciais reais serem executadas.

## CSRF, sessões e APIs privadas

`GET /api/csrf` devolve `token` e `headerName`. O navegador deve manter o cookie CSRF e enviar o token no
header `X-XSRF-TOKEN` de todo POST, inclusive nas partidas de visitantes. O módulo `http-client.js` faz isso
para contas e jogo; uma resposta 403 de CSRF pede atualização da página. CORS continua restrito à mesma origem.

`/api/me`, `/api/logout` e `/api/account/delete` exigem autenticação. Os endpoints do jogo aceitam visitantes,
mas o proprietário de uma rodada autenticada é conferido pelo servidor. Pontuações do ranking são calculadas
no Java e inseridas de forma idempotente. Cada API autenticada consulta a sessão uma vez; os controllers usam
o usuário já resolvido no `SecurityContext`, sem outra consulta de identidade.

O `JSESSIONID` temporário é usado pelo Spring para state/nonce/PKCE do OAuth. Após o login ele é invalidado.
A única sessão de jogo é `geoquest_session`, aleatória, HttpOnly, SameSite=Lax e Secure em HTTPS, com validade
de sete dias. Somente seu hash SHA-256 fica no banco. Não há JWT, Basic Auth ou segunda base de usuários.
Tokens do provedor são usados temporariamente pelo Spring e descartados ao concluir o login; não vão para
as tabelas do jogo. Exclusão requer login nos últimos dez minutos e confirmação DELETE. A exclusão não apaga
a conta Google/GitHub nem revoga automaticamente as permissões externas; isso pode ser feito nos provedores.

## Testes automatizados

    mvn test
    node --test frontend/tests/*.test.js

Os testes normais não exigem credenciais de produção. Para a suíte PostgreSQL, use um banco descartável chamado
`geoquest_test`, role `geoquest_test`, senha `test-only-password`, e defina `TEST_DATABASE_URL` com sua URL JDBC.
A suíte valida o nome dedicado antes de limpar dados de teste. Não use a URL do Neon publicado.

## Referências

- Spring Boot/Java: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- OAuth2 Login: https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/login/advanced.html
- CSRF: https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html
- Google: https://developers.google.com/identity/protocols/oauth2/web-server
- GitHub: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app
- Render: https://render.com/docs/docker
- Neon/JDBC: https://neon.com/docs/guides/java
