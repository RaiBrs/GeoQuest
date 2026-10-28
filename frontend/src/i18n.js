const portuguese = {
  "QUESTION {number}": "PERGUNTA {number}",
  "Time remaining": "Tempo restante",
  "{name}, you got {score} correct answers from {answered} questions in 60 seconds.": "{name}, você acertou {score} de {answered} perguntas respondidas em 60 segundos.",
  "60 seconds. Unlimited questions. Keep going after mistakes.": "60 segundos. Perguntas sem limite. Continue após os erros.",
  "See where I went wrong": "Veja onde eu errei",
  "Review your mistakes below. Unanswered questions are not counted as mistakes.": "Revise seus erros abaixo. Perguntas sem resposta não contam como erros.",
  "No mistakes in the answers you submitted. Unanswered questions are not counted as mistakes.": "Você não errou nas respostas enviadas. Perguntas sem resposta não contam como erros.",
  "Question {number}: {text}": "Pergunta {number}: {text}",
  "Your answer: {answer}": "Sua resposta: {answer}",
  "Correct answer: {answer}": "Resposta correta: {answer}",
  "YOUR NEXT CHALLENGE": "SEU PRÓXIMO DESAFIO",
  "Explore the world.": "Explore o mundo.",
  "Make your mark.": "Deixe sua marca.",
  "Put your knowledge of world capitals to the test, one answer at a time.": "Teste seus conhecimentos de capitais do mundo, uma resposta por vez.",
  "WORLD CAPITALS": "CAPITAIS DO MUNDO",
  "Start your journey": "Comece sua jornada",
  "Tell us what to call you during the game.": "Diga como podemos chamar você durante o jogo.",
  "Your name": "Seu nome",
  "What should we call you?": "Como podemos chamar você?",
  "Up to 80 characters. Used only to greet you.": "Até 80 caracteres. Usado apenas nas saudações.",
  "Choose your challenge": "Escolha seu desafio",
  "Three ways to explore. Choose your challenge in the hub.": "Três maneiras de explorar. Escolha seu desafio na central.",
  "YOUR WORLD OF CHALLENGES": "SEU MUNDO DE DESAFIOS",
  "Choose how you want to explore world capitals today.": "Escolha como explorar a geografia hoje.",
  "LEARN AT YOUR PACE": "APRENDA NO SEU RITMO",
  "Capitals": "Capitais",
  "Answer all 10 questions. Mistakes are part of the journey.": "Responda às 10 perguntas. Errar faz parte da jornada.",
  "Play Capitals": "Jogar Capitais",
  "MAKE EVERY ANSWER COUNT": "CADA RESPOSTA CONTA",
  "Survival": "Sobrevivência",
  "One mistake ends the round. Can you get all 10 right?": "Um erro encerra a rodada. Você consegue acertar as 10?",
  "Play Survival": "Jogar Sobrevivência",
  "BEAT THE CLOCK": "VENÇA O RELÓGIO",
  "Against the clock": "Contra o tempo",
  "Play Against the clock": "Jogar Contra o tempo",
  "Change player": "Trocar jogador",
  "Next question": "Próxima pergunta",
  "Play again": "Jogar novamente",
  "ANOTHER JOURNEY AROUND THE WORLD": "MAIS UMA VIAGEM PELO MUNDO",
  "Round complete": "Rodada concluída",
  "Back to hub": "Voltar à central",
  "Try again": "Tentar novamente",
  "Your name. Your journey. Your mark on the world.": "Seu nome. Sua jornada. Sua marca no mundo.",
  "Language": "Idioma",
  "Question type": "Tipo de pergunta",
  "Find the capital": "Descubra a capital",
  "Find the country": "Descubra o país",
  "Correct answers: {score}": "Acertos: {score}",
  "QUESTION {number} OF {total}": "PERGUNTA {number} DE {total}",
  "Your turn, {name}.": "Sua vez, {name}.",
  "Hello, {name}!": "Olá, {name}!",
  "Time remaining: {seconds}s": "Tempo restante: {seconds}s",
  "Only {seconds} seconds left!": "Restam apenas {seconds} segundos!",
  "CAPITALS · 10 QUESTIONS": "CAPITAIS · 10 PERGUNTAS",
  "SURVIVAL · ONE MISTAKE ENDS THE ROUND": "SOBREVIVÊNCIA · UM ERRO ENCERRA A RODADA",
  "AGAINST THE CLOCK · 60 SECONDS": "CONTRA O TEMPO · 60 SEGUNDOS",
  "Press A–D to answer. After answering, press Space to continue.": "Pressione A–D para responder. Após responder, use Espaço para continuar.",
  "Press R to play again or Space to view your result.": "Pressione R para jogar novamente ou Espaço para ver o resultado.",
  "✓ Correct": "✓ Correta",
  "Your answer": "Sua resposta",
  "Well done, {name}! Correct answer.": "Muito bem, {name}! Resposta correta.",
  "Not this time, {name}. The correct answer is {answer}.": "Não foi desta vez, {name}. A resposta correta é {answer}.",
  "View result": "Ver resultado",
  "Next question →": "Próxima pergunta →",
  "Time is up!": "O tempo acabou!",
  "Perfect round!": "Rodada perfeita!",
  "Survival ended": "Fim da sobrevivência",
  "{name}, you answered {score} out of {total} questions correctly.": "{name}, você acertou {score} de {total} perguntas.",
  "Preparing your round…": "Preparando sua rodada…",
  "Checking your answer…": "Verificando sua resposta…",
  "Enter a name of up to 80 characters, without control characters.": "Digite um nome com até 80 caracteres, sem caracteres de controle.",
  "Unable to complete the operation.": "Não foi possível concluir a operação.",
  "Unable to complete the operation. Please try again.": "Não foi possível concluir a operação. Tente novamente.",
  "Round not found. Start a new game.": "Rodada não encontrada ou expirada. Comece um novo jogo.",
  "This round has already ended.": "Esta rodada já terminou.",
  "This is not the current question.": "Esta não é a pergunta atual.",
  "Invalid answer choice.": "Alternativa inválida.",
  "Unable to connect. Check your connection and try again.": "Não foi possível conectar. Verifique sua conexão e tente novamente.",
  "The server sent an unexpected response. Please try again shortly.": "O servidor enviou uma resposta inesperada. Tente novamente em instantes.",
  "Questions answered": "Perguntas respondidas",
  "GeoQuest: back to home": "GeoQuest: voltar ao início"
};
let language = "EN";
export function getLanguage() { return language; }
export function t(key, values = {}) {
  const template = language === "PT_BR" ? (portuguese[key] || portuguese["Unable to complete the operation."]) : key;
  return template.replace(/\{(\w+)\}/g, (_, name) => String(values[name] ?? ""));
}
export function setLanguage(next) {
  language = next === "PT_BR" ? "PT_BR" : "EN";
  document.title = language === "PT_BR" ? "GeoQuest — Explore o mundo" : "GeoQuest — Explore the world";
  if (document.documentElement) document.documentElement.lang = language === "PT_BR" ? "pt-BR" : "en";
  for (const node of document.querySelectorAll("[data-i18n]")) {
    if (node.dataset.i18n) node.textContent = t(node.dataset.i18n);
  }
  for (const node of document.querySelectorAll("[data-i18n-placeholder]")) {
    if (node.dataset.i18nPlaceholder) node.setAttribute("placeholder", t(node.dataset.i18nPlaceholder));
  }
  for (const node of document.querySelectorAll("[data-i18n-aria]")) {
    if (node.dataset.i18nAria) node.setAttribute("aria-label", t(node.dataset.i18nAria));
  }
}
