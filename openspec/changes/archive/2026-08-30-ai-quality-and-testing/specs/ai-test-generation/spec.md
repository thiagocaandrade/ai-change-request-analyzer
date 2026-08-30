## Purpose

Geração e refinamento de recomendações de teste com IA a partir dos findings da revisão, sempre como sugestões justificadas que o usuário pode adotar — nunca como alterações automáticas.

## ADDED Requirements

### Requirement: Recomendações de teste geradas a partir dos findings

O sistema DEVE gerar recomendações de teste a partir dos findings da revisão, cada uma com descrição, componente alvo e justificativa ligada ao risco identificado.

#### Scenario: Recomendações geradas com justificativa

- **WHEN** a revisão produziu findings sobre uma alteração
- **THEN** o sistema gera recomendações de teste, cada uma com descrição, componente alvo e justificativa referenciando o finding de origem

#### Scenario: Sem findings, sem recomendações de modelo

- **WHEN** a revisão não produziu findings (degradada ou sem riscos identificados)
- **THEN** o sistema não inventa recomendações e indica explicitamente que nenhuma foi gerada pelo modelo

### Requirement: Refinamento limitado e registrado

Quando uma recomendação gerada é inválida ou incompleta, o sistema DEVE refiná-la com no máximo 2 iterações, registrando cada tentativa; esgotado o limite, DEVE manter a recomendação marcada como não refinada.

#### Scenario: Refinamento dentro do limite

- **WHEN** a primeira recomendação é inválida e a tentativa seguinte é válida
- **THEN** a recomendação válida é usada e as tentativas são registradas

#### Scenario: Limite de refinamento esgotado

- **WHEN** todas as tentativas de refinamento falham
- **THEN** a recomendação permanece marcada como não refinada com erro estruturado registrado, sem loop infinito

### Requirement: Recomendações nunca aplicadas automaticamente

Nenhuma recomendação de teste DEVE ser escrita, executada ou aplicada ao repositório pelo sistema; recomendações são entregues ao usuário como dado estruturado.

#### Scenario: Recomendação apenas sugerida

- **WHEN** o sistema gera recomendações de teste
- **THEN** nenhum arquivo de teste é criado ou alterado automaticamente

### Requirement: Registro de prompt e resultado da geração

Cada execução de geração/refinamento DEVE registrar o prompt versionado usado, o resultado estruturado e as iterações realizadas, correlacionados pelo trace_id.

#### Scenario: Geração registrada

- **WHEN** recomendações de teste são geradas ou refinadas
- **THEN** o registro persistido contém prompt versionado, resultado, iterações e trace_id

### Requirement: Geração degradada sem modelo

Quando nenhum modelo de IA está configurado, a etapa DEVE seguir com lista vazia marcada e a análise DEVE concluir sem erro.

#### Scenario: Geração sem modelo

- **WHEN** o sistema não possui modelo de IA configurado
- **THEN** a etapa retorna lista vazia marcada como degradada e o fluxo conclui normalmente
