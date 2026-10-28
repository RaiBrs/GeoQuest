# Revisão e migração da autenticação do GeoQuest

## Resultado

A camada HTTP/autenticação migrou para Spring Boot 3.5.16 + Spring Security 6.5.11, com Java 25.
As regras do jogo, os modos, a localização, a revisão de erros e o cálculo de pontos continuam no Java
existente. Os dados e as sessões persistentes usam o mesmo PostgreSQL. Não foram adicionados JWT,
Basic Auth, JPA, Supabase ou uma segunda estrutura de usuários.

A implementação usa a branch `feat/spring-security-oauth2` e é integrada na branch de publicação
`feat/render-neon-accounts`. As migrações 001/002 foram aplicadas no Neon após validação isolada,
e as credenciais OAuth foram configuradas diretamente no Render. Confirme o consentimento e o
retorno dos provedores com uma conta real após cada atualização da autenticação.

## Análise inicial

| Item | Antes da mudança |
|---|---|
| Spring Boot/Security | Nenhum; o pom usava Java, Jackson, PostgreSQL e JUnit |
| Servidor | `com.sun.net.httpserver.HttpServer`, registrado manualmente em WebApp |
| SecurityConfig | Não existia |
| Autenticação | OAuth manual com Google/GitHub, state/PKCE e cookie próprio |
| Sessões | `geoquest_session`, hash SHA-256 no banco, sete dias de validade |
| JWT / Basic Auth | Ausentes |
| Usuários | Perfis e identidades SQL; identidade estável por provider + subject |
| Persistência | PostgresAccounts com JDBC e prepared statements |
| Contratos | GameDtos, registros de conta/perfil e respostas JSON próprias |
| Erros | JsonHttp centralizava mensagens, status e fechamento do HttpExchange |

Os conflitos eram de infraestrutura: SecurityFilterChain trabalha na cadeia Servlet, enquanto o servidor
anterior era o HttpServer do JDK; os callbacks e a busca da identidade eram manuais. A migração foi
confirmada pelo usuário antes de editar a aplicação. Não bastava adicionar dois starters ao pom.

## Fluxo OAuth2

1. O frontend abre `/oauth2/authorization/google` ou `/oauth2/authorization/github`.
2. Spring Security cria o pedido de autorização, state/PKCE e, para Google OIDC, nonce. O JSESSIONID
   temporário guarda o estado necessário ao protocolo.
3. O provedor retorna a `/login/oauth2/code/google` ou `/login/oauth2/code/github`.
4. Spring valida o retorno, troca o código e obtém/verifica os dados do provedor. Não há troca manual
   de tokens, construção manual de URLs de autorização ou validação criptográfica própria no projeto.
5. OAuthUserNormalizer transforma os atributos já verificados em SocialIdentity. Google usa sub/picture;
   GitHub usa id/avatar_url e login como alternativa de nome. Email ausente é permitido.
6. OAuthLoginSuccessHandler reutiliza PostgresAccounts para recuperar/criar o perfil e AccountSessions
   para emitir a sessão persistente do jogo. O token aleatório vai para um cookie HttpOnly; apenas seu
   hash vai para a tabela player_sessions.
7. A sessão temporária é invalidada e o cliente autorizado/tokens do provedor são descartados. Nas APIs,
   PersistentSessionFilter recupera o usuário uma vez e popula o SecurityContext daquela requisição.

O `NullSecurityContextRepository` é intencional: evita guardar outra autenticação persistente no
JSESSIONID. A sessão duradoura do jogo continua sendo a que já existe no banco. O logout padrão do
Spring é substituído pelo endpoint de logout existente, que revoga essa sessão no PostgreSQL.

### Identidades e usuários existentes

A chave continua provider + providerId; email e nome não são chaves de autenticação. Logins repetidos
com a mesma identidade recuperam o mesmo UUID, inclusive sob concorrência. Contas Google e GitHub não
são unidas automaticamente por email, que pode estar ausente ou não ter a mesma garantia de verificação.
Vincular contas com prova de posse dos dois provedores é uma evolução separada.

Email e avatar são opcionais, privados e preservados quando um novo login não os fornece. A migração
002 só adiciona colunas e preenche o provedor de sessões antigas quando há exatamente uma identidade.
Nenhuma tabela, perfil, sessão ou pontuação existente é descartada por essa migração.

## Endpoints públicos e privados

| Método / rota | Acesso |
|---|---|
| GET `/`, arquivos explicitamente permitidos e `/privacy.html` | Público |
| GET `/api/account` | Público; informa disponibilidade e perfil da sessão atual |
| GET `/api/csrf` | Público; entrega token CSRF e nome do header |
| GET `/api/leaderboard` | Público quando as contas estão configuradas |
| POST `/api/games`, `/api/answers`, `/api/games/status` | Público com CSRF; a rodada autenticada exige seu proprietário |
| GET `/oauth2/authorization/{provider}` | Público, apenas provedores configurados |
| GET `/login/oauth2/code/{provider}` | Callback validado pelo Spring |
| GET `/api/me` | Autenticado; nome, email opcional, provider, providerId, avatarUrl opcional |
| POST `/api/logout` | Autenticado + CSRF |
| POST `/api/account/delete` | Autenticado + CSRF + login recente + confirmação DELETE |

A lista de arquivos públicos continua explícita. Arquivos locais, .env, código de teste e outras partes
do repositório não são servidos. Rotas removidas continuam 404. Métodos incorretos continuam 405.
A regra da cadeia exige login nas três rotas privadas atuais; novas APIs privadas devem ser adicionadas
explicitamente à política de autorização e aos testes.

## Bugs e problemas corrigidos

- **Erro HTTP com corpo null no frontend:** o cliente de contas acessava body.message sem verificar
  o corpo. Uma resposta null podia causar TypeError e esconder a falha original. O cliente comum agora
  usa uma mensagem segura de fallback; há teste de regressão.
- **Provedor OAuth inexistente retornando 500:** confirmado no teste da nova cadeia Spring.
  ConfiguredOAuthRequestResolver verifica a existência do registro e deixa a rota desconhecida chegar
  ao 404 do MVC. Todo o protocolo segue delegado ao resolver do Spring.
- **Verificação parcial do banco:** antes o startup consultava só player_sessions. Agora verifica
  também os campos de perfil, identidades e resultados necessários. Falhas de conexão não são mais
  descritas apenas como uma instrução de aplicar SQL; o diagnóstico menciona conexão e migração.
- **Diagnóstico JDBC sem contexto:** falhas agora registram SQLSTATE/código, sem SQL, senha ou dados
  do usuário. Erros inesperados HTTP registram classe e pontos da stack, sem imprimir mensagens/causas
  que possam conter segredos. O cliente continua recebendo respostas genéricas.
- **Configuração local Docker com aspas:** a rodada real falhou porque --env-file preservou as aspas
  do .env existente na chave da API. O arquivo original foi preservado. O comando local documentado
  agora monta .env somente para leitura e deixa o parser Java interpretar o arquivo. Uma rodada de
  dez perguntas foi concluída no navegador depois de corrigir a forma de fornecer a configuração.

Durante a migração, também foram corrigidos problemas dos adaptadores de teste MVC e do mapeamento
curinga que mascarava respostas 404/405. Os testes legítimos de contratos foram preservados, com a
adaptação do transporte; não se trocaram expectativas só para aceitar uma regressão.

## Duplicação, refatorações e otimizações

- O OAuth manual foi removido; Spring fornece redirecionamento, state/PKCE, troca de código, UserInfo
  e OIDC. A aplicação ficou responsável por normalizar o perfil e por suas regras de conta.
- A normalização Google/GitHub está em um único ponto. Controllers e JDBC não fazem parsing diferente
  de atributos de cada provedor.
- O frontend usa um único http-client para JSON, erros, credenciais e CSRF. O pedido do token CSRF é
  compartilhado e reaproveitado; falha na busca libera uma tentativa posterior.
- O usuário é recuperado uma vez no filtro por requisição de API que precisa dessa identidade. Assets,
  CSRF e ranking público não provocam buscas de sessão desnecessárias.
- As exceptions de interrupção artificiais do contrato JDBC foram removidas. A chamada ao catálogo de
  países continua tratando interrupção, pois essa operação de rede realmente pode produzi-la.
- Spring MVC cuida de roteamento/JSON e ApiErrors concentra as respostas de falha. O limite de 4096
  bytes do corpo JSON foi preservado por RequestBodyAdvice.
- A inicialização e a tarefa de limpeza passaram a beans/agendamento do Spring. GameService e os
  modelos continuam sem dependência de Spring.
- O Docker usa o JAR executável, sem uma cópia separada de dependências de runtime.
- Não foram introduzidos ORM, hierarquias de serviços, cache de usuários ou abstrações genéricas de
  provedores para eliminar poucas linhas. O JDBC existente foi reutilizado.

## Segurança preservada e acrescentada

- CSRF permanece ativo em todos os POSTs. O cookie é HttpOnly; o token mascarado é obtido por
  `/api/csrf` e enviado pelo cliente no header `X-XSRF-TOKEN`.
- CORS não foi aberto. Frontend e API usam a mesma origem.
- OAuth usa state, PKCE e a validação OIDC do framework para Google. O callback deriva do APP_URL
  validado, sem confiar no hostname enviado pelo cliente para escolher a URI cadastrada.
- Sessões têm token aleatório, hash no banco, expiração, revogação por logout/exclusão e cookies
  HttpOnly/SameSite=Lax/Secure sobre HTTPS. A identidade do usuário não vem do JSON da rodada.
- Chamadas de token/UserInfo dos provedores têm conexão limitada a 5 segundos e leitura a 10 segundos,
  com componentes do próprio Spring e redirecionamentos HTTP desativados nesses clientes.
- Pontuações seguem calculadas no servidor e salvas com idempotência por game_id.
- Exclusão exige login nos últimos dez minutos e confirmação explícita; cascatas removem os vínculos.
- Senhas e Client Secrets ficam nas variáveis do servidor. Não foram escritos em arquivos versionados,
  respostas de perfil, logs ou commits. .env permanece ignorado e fora da imagem Docker.
- Email/avatar opcionais foram incluídos no aviso de dados; eles não aparecem no ranking público.

## Variáveis e configuração externa

Necessárias para os dois provedores: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`,
`GITHUB_CLIENT_SECRET`. Também são usados `APP_URL`, `ACCOUNTS_ENABLED`, `API_KEY`, `JDBC_DATABASE_URL`,
`DATABASE_USER` e `DATABASE_PASSWORD`. Um par de provedor pode ficar ausente; outro precisa estar completo
quando as contas são habilitadas.

Os passos para obter credenciais no Google Auth Platform e em GitHub Settings → Developer settings →
OAuth Apps estão em [DEPLOYMENT.md](DEPLOYMENT.md). Não envie segredos pelo chat.

| Provedor | Callback local, porta padrão | Callback no Render existente |
|---|---|---|
| Google | `http://localhost:8080/login/oauth2/code/google` | `https://geoquest-raibrs.onrender.com/login/oauth2/code/google` |
| GitHub | `http://localhost:8080/login/oauth2/code/github` | `https://geoquest-raibrs.onrender.com/login/oauth2/code/github` |

## Validação executada

- `mvn verify` com PostgreSQL local descartável: **130 testes Java, zero falhas, zero testes pulados**, incluindo HEAD no Tomcat real.
- `node --test frontend/tests/*.test.js`: **8 testes aprovados**.
- Docker: imagem `geoquest:spring-oauth2` construída; os cinco testes externos de PostgreSQL são pulados
  dentro do build sem TEST_DATABASE_URL, e foram executados separadamente no banco descartável.
- Navegador: fluxo em português, criação de partida, dez respostas e resultado final com CSRF ativo.
- Segurança: 401 sem login em rotas privadas, rejeição de sessão inválida, 403 sem CSRF, state inválido,
  PKCE/callbacks separados, provedor inexistente, sessão opaca, descarte de tokens, exclusão recente e
  proibição de acesso à rodada de outro usuário.
- Banco: identidade estável, oito logins simultâneos com um único perfil, migração sem perder sessão
  ou score, email opcional preservado, expiração, idempotência de resultados e exclusão em cascata.

As credenciais foram configuradas no Render. Os testes com identidades controladas não substituem
o consentimento e o retorno com uma conta real, que devem ser validados após a publicação.

## Como testar

Execute `mvn spring-boot:run` na pasta do pom e abra http://localhost:8080. Para testar apenas visitantes,
configure API_KEY e mantenha ACCOUNTS_ENABLED=false. Para contas, aplique 001/002 e configure as variáveis
acima. Teste cada `/oauth2/authorization/...`, confira `/api/me`, finalize uma rodada, consulte ranking,
faça logout e teste exclusão com uma conta de teste. Sem sessão, `/api/me` deve responder 401; alterações
sem token CSRF devem responder 403. O guia de publicação descreve todos os passos.

## Pendências e aprendizado

- Testar consentimento e retorno reais com os clientes Google/GitHub configurados, incluindo GitHub sem email público.
- Confirmar os callbacks externos. A migração 002 já foi testada em branch isolada e aplicada no banco da aplicação.
- Revisar a integração pelo pull request; o serviço mantém a branch de publicação `feat/render-neon-accounts`.
- Vinculação entre provedores requer um fluxo explícito de prova de posse, fora desta mudança.
- Rate limiting, prevenção de automação e limites de recursos continuam como evolução para uma
  publicação maior; não foram adicionados como parte desta migração de autenticação.

O conceito central a estudar é a diferença entre **identidade**, **sessão** e **autorização**: OAuth prova
quem é o jogador; o cookie permite reconhecê-lo em novos pedidos; as regras da cadeia e do jogo definem
quais operações ele pode fazer. Spring implementa o protocolo e os filtros, enquanto as regras e o
armazenamento da aplicação continuam responsabilidade do código Java existente.

## Inventário de arquivos

Os caminhos abaixo são relativos à raiz do projeto.

- **Modificado**: `.env.example`
- **Modificado**: `Dockerfile`
- **Modificado**: `README.md`
- **Modificado**: `docs/DEPLOYMENT.md`
- **Modificado**: `frontend/index.html`
- **Modificado**: `frontend/privacy.html`
- **Modificado**: `frontend/src/controller/account-controller.js`
- **Modificado**: `frontend/src/i18n.js`
- **Modificado**: `frontend/src/service/game-service.js`
- **Modificado**: `frontend/tests/account-controller.test.js`
- **Modificado**: `frontend/tests/app.test.js`
- **Modificado**: `frontend/tests/game-service.test.js`
- **Modificado**: `pom.xml`
- **Modificado**: `src/main/java/com/raibrs/geoquest/WebApp.java`
- **Modificado**: `src/main/java/com/raibrs/geoquest/account/AccountController.java`
- **Modificado**: `src/main/java/com/raibrs/geoquest/account/AccountsBackend.java`
- **Removido**: `src/main/java/com/raibrs/geoquest/account/OAuthIdentity.java`
- **Modificado**: `src/main/java/com/raibrs/geoquest/account/PostgresAccounts.java`
- **Modificado**: `src/main/java/com/raibrs/geoquest/controller/GameController.java`
- **Removido**: `src/main/java/com/raibrs/geoquest/http/JsonHttp.java`
- **Modificado**: `src/main/java/com/raibrs/geoquest/http/StaticFileHandler.java`
- **Modificado**: `src/test/java/com/raibrs/geoquest/WebAppTest.java`
- **Modificado**: `src/test/java/com/raibrs/geoquest/account/AccountControllerTest.java`
- **Removido**: `src/test/java/com/raibrs/geoquest/account/OAuthIdentityTest.java`
- **Modificado**: `src/test/java/com/raibrs/geoquest/account/PostgresAccountsTest.java`
- **Modificado**: `src/test/java/com/raibrs/geoquest/controller/GameControllerTest.java`
- **Modificado**: `src/test/java/com/raibrs/geoquest/support/TestHttpServer.java`
- **Criado**: `database/002_oauth_profile.sql`
- **Criado**: `docs/SPRING_OAUTH_REVIEW.md`
- **Criado**: `frontend/src/service/http-client.js`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/AccountSessions.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/ConfiguredOAuthRequestResolver.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/OAuthLoginSuccessHandler.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/OAuthUserNormalizer.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/PersistentSessionFilter.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/account/SocialIdentity.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/config/OAuthClientConfig.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/config/RoundCleanup.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/config/SecurityConfig.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/http/ApiErrors.java`
- **Criado**: `src/main/java/com/raibrs/geoquest/http/JsonBodyLimit.java`
- **Criado**: `src/main/resources/application.properties`
- **Criado**: `src/test/java/com/raibrs/geoquest/account/OAuthUserNormalizerTest.java`

## Verificação adicional de HEAD

A versão antiga publicou a correção 6a2d403 para HEAD em uma branch separada da migração. Na migração Spring, um teste adicional usa Tomcat real para conferir cabeçalhos e corpo vazio em páginas, assets e respostas de erro. Os quatro testes de HEAD/arquivos passaram; MockMvc isoladamente não reproduz a supressão do corpo feita pelo transporte Servlet.

Arquivos adicionais desta verificação:

- **Modificado**: `src/test/java/com/raibrs/geoquest/http/StaticFileHandlerTest.java`
- **Criado**: `src/test/java/com/raibrs/geoquest/http/HeadRequestTest.java`
