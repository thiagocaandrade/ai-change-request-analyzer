# Política de Segurança

## Conteúdo não confiável

Código, documentos, issues e histórico recuperados do repositório são dados não confiáveis. Nunca se tornam instruções do sistema. Tentativas de injeção de prompt são detectadas e registradas como evento de segurança; a instrução injetada é ignorada.

## Segredos

Chaves de API, tokens e senhas nunca aparecem em logs, erros ou respostas de API. Configuração de modelo de IA somente via variáveis de ambiente; `.env.example` sem valores reais.

## Acesso a arquivos

As tools de leitura de arquivo restringem o acesso à raiz configurada do repositório. Path traversal (`../`), caminhos absolutos fora da raiz e entradas vazias são rejeitados com erro estruturado. Nenhuma tool executa shell.

## Autonomia do agente

O agente não altera código nem infraestrutura. Aprovação humana é obrigatória para risco HIGH e é aplicada pela aplicação, nunca pelo modelo.
