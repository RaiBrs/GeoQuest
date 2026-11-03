# Guia de leitura do código

Este mapa explica onde procurar cada responsabilidade. Os comentários junto ao código
registram decisões e cuidados que não ficam evidentes pelo nome do método.

## Onde está cada parte

| Caminho | Responsabilidade |
|---|---|
| `frontend/index.html` | Estrutura, rótulos e telas. O JavaScript alterna a tela visível. |
| `frontend/privacy.html` | Explicação pública sobre dados e contas. |
| `frontend/assets/` | Marca e ícone usados pela interface. |
| `frontend/src/app.js` | Liga os controllers de jogo e conta pela disponibilidade da interface. |
| `frontend/src/controller/` | Eventos, navegação, requisições e estado do navegador. |
| `frontend/src/service/` | Endpoints do jogo e transporte HTTP com CSRF. |
| `frontend/src/view/` | Menu, foco e utilitários do DOM. |
| `frontend/src/view/pages/` | Renderização e controles de cada página. Não calcula pontuação. |
| `frontend/src/i18n.js` | Traduções da interface e preferência de idioma. |
| `frontend/styles/tokens.css` | Cores, fontes e medidas compartilhadas. |
| `frontend/styles/base.css` | Estilos nativos, foco, ocultação e redução de movimento. |
| `frontend/styles/layout.css` | Cabeçalho, menu e distribuição geral. |
| `frontend/styles/components.css` | Botões, avisos, perfil e painel de ranking. |
| `frontend/styles/pages/` | Estilos específicos de entrada, desafios, jogo e resultado. |
| `frontend/tests/` | Testes de interação, tradução, HTTP e renderização segura. |
| `frontend/tests/support/` | DOM e relógio simulados, reiniciados para cada cenário de jogo. |
| `src/main/java/com/raibrs/geoquest/WebApp.java` | Inicialização do Spring e montagem dos serviços. |
| `…/controller/` | Endpoints das partidas. Retorna apenas os contratos públicos. |
| `…/dto/` | Formatos JSON aceitos e devolvidos pela API. |
| `…/service/` | Coordenação, geração de perguntas e armazenamento das partidas. |
| `…/model/` | Regras de pontuação, término, perguntas e localização geográfica. |
| `…/api/` | Busca e validação dos dados externos de países. |
| `…/account/` | Identidade OAuth, sessão do jogador, perfil e ranking. |
| `…/config/` | Ambiente, segurança, clientes OAuth e estado compartilhado. |
| `…/http/` | Arquivos públicos, limite do corpo JSON e mensagens de erro. |
| `src/main/resources/` | Configuração do Spring e catálogo local de capitais. |
| `src/test/java/` | Testes Java, incluindo HTTP, autenticação e PostgreSQL. |
| `database/` | Migrações ordenadas; não recriam o banco publicado. |
| `docs/` e `docs/assets/` | Guias, registros de validação e imagens da documentação. |
| `Dockerfile` e `vercel.json` | Construção do container e configuração da publicação. |
| `target/` | Saída gerada pelo Maven e verificações locais; não é código-fonte. |

As pastas Java indicadas por `…/` ficam sob `src/main/java/com/raibrs/geoquest/`.
Pastas da IDE e arquivos `.env` são locais e não fazem parte do funcionamento público.

## Uma resposta, do clique até a correção

1. `game-view` recebe o clique ou atalho; `game-controller` bloqueia novos envios.
2. `game-service.js` escolhe o endpoint; `http-client` envia cookie e token CSRF.
3. O Spring verifica CSRF; a sessão persistente identifica o jogador quando existe.
4. `GameController` pede a operação ao `GameService`, que valida dono e validade da partida.
5. `GameSession` calcula acerto, pontos e término. A operação guarda o resultado do último envio.
6. O controller salva resultados de contas quando a rodada terminou. A API retorna o mesmo JSON de antes.
7. A view mostra a correção. No modo cronometrado, o próximo enunciado aparece após 700 ms.

Repetir o último envio retorna a mesma correção sem pontuar duas vezes. Enquanto a correção
está visível, o estado recebido já pode conter a próxima pergunta: essa diferença é intencional.

## Quem controla a interface

A shell controla navegação e carregamento. As views de jogo, desafios, entrada, resultado e conta
controlam seus próprios botões. O controller informa a disponibilidade comum, sem reativar botões
de outras funcionalidades. Abrir e fechar o menu permanece possível durante requisições.

Navegar para outra seção cancela os temporizadores e limpa a rodada local. A rodada no servidor
segue sua política de expiração. O relógio do navegador exibe o tempo; o servidor decide o término.

O ranking mantém suas linhas durante mudanças de disponibilidade. Dados novos, troca de filtros,
exclusão da conta ou troca de idioma atualizam a tabela. Nomes externos entram como texto.

## Duas sessões com propósitos diferentes

- **Login OAuth:** sessão temporária de dez minutos usada pelo Spring para conferir o retorno do
  provedor, incluindo `state` e PKCE. Pode ficar no PostgreSQL quando o estado compartilhado está ativo.
- **Sessão do jogador:** token aleatório no cookie `geoquest_session`, com hash salvo no banco e
  validade de sete dias. Logout revoga a sessão. Exclusão exige autenticação nos últimos dez minutos.

Google e GitHub são identificados por provedor e identificador próprio; nomes e e-mails não unem
contas. Tokens de acesso do provedor são descartados após o login.

## Partidas em memória ou compartilhadas

Sem estado compartilhado, cada partida é protegida por um lock em memória. Com PostgreSQL,
uma transação e um lock de linha protegem leitura, alteração e gravação entre instâncias.

`RoundSnapshot` codifica o formato interno já existente no banco, incluindo gabaritos e histórico
de retry. `GameDtos` define o que pode chegar ao navegador. Esses formatos têm propósitos diferentes.
O tipo da pergunta acompanha o resultado interno para salvar a pontuação sem reabrir a partida.

## Como revisar uma alteração

Execute `node --test frontend/tests/*.test.js` e `mvn test`. O frontend não possui etapa própria de
build, lint ou typecheck. Os testes Java com PostgreSQL exigem um banco descartável chamado
`geoquest_test`; consulte [DEPLOYMENT.md](DEPLOYMENT.md) para as variáveis de teste.

O DOM simulado verifica lógica e conteúdo, sem reproduzir layout, leitor de tela ou todos os detalhes
de foco do navegador. Mudanças de interface também precisam de verificação visual e por teclado.
O consentimento real de Google/GitHub depende dos provedores e complementa os testes locais de OAuth.

## Verificação desta refatoração — 3 de novembro de 2026

- `mvn verify`: 144 testes passaram, sem falhas ou testes ignorados, com PostgreSQL temporário local.
- Frontend: 22 testes passaram; retry, temporizadores, controles e idioma têm cenários independentes.
- Navegador com dados fictícios: entrada, resposta, resultado, modo cronometrado, idioma e ranking indisponível.
- Em 360 px: sem rolagem horizontal no ranking; menu modal, Escape e retorno de foco conferidos.
- Segurança: testes de CSRF, acesso entre jogadores, logout, cookies, `state`, PKCE e descarte de tokens passaram.
- `git diff --check` passou. O consentimento real dos provedores não foi repetido nesta revisão.
