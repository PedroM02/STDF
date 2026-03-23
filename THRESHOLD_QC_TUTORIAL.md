# Threshold Signatures Para Certificados/Quoruns

Este guia assume o seguinte objetivo:

- manter `PerfectLink`, `RetryLink` e transporte como estão
- usar `threshold signatures` apenas para `QuorumCertificate`
- nao usar threshold signatures para `ACK`s nem para cada mensagem `DATA`

## 1. Nao mexer no link layer

Nao deves alterar estas classes para introduzir threshold signatures:

- `pt.ulisboa.tecnico.sdtf/src/links/PerfectLink.java`
- `pt.ulisboa.tecnico.sdtf/src/links/RetryLink.java`
- `pt.ulisboa.tecnico.sdtf/src/transport/UdpTransport.java`

Estas classes tratam de:

- envio
- retransmissao
- `ACK`
- deduplicacao

Threshold signatures nao servem para isto. Servem para provar que um quorum aprovou uma decisao.

## 2. Redefinir o papel do QuorumCertificate

Hoje o `QuorumCertificate` mistura:

- contagem de votos
- armazenamento de votos
- verificacao RSA de votos individuais

Com threshold signatures, o `QuorumCertificate` deve passar a representar apenas a prova final de quorum.

O `QuorumCertificate` deve conter pelo menos:

- `blockHash`
- `view`
- `phase`
- `configVersion` ou `epoch`
- `threshold`
- assinatura final agregada
- opcionalmente os participantes usados ou bitmap de participantes

Deixa de fazer sentido guardar `List<Vote>` dentro do certificado final.

## 3. Definir bytes canonicos do certificado

Antes de assinar, deves construir uma representacao canonica do QC.

Exemplo de campos a incluir:

- domain separator, por exemplo `HOTSTUFF_QC`
- `blockHash`
- `view`
- `phase`
- `configVersion`

Se quiseres ser mais defensivo, inclui tambem:

- `chainId` ou identificador do sistema
- `membership epoch`

Objetivo:

- evitar replay de assinaturas
- impedir reutilizacao da mesma assinatura noutra fase ou view

## 4. Criar configuracao threshold

Cria uma configuracao nova para o material criptografico threshold.

Exemplo logico:

- `n`
- `t`
- `groupPublicKey`
- `participantIndex` do no local
- `privateShare` local
- mapeamento estavel `ProcessId -> participantIndex`

Este mapeamento tem de ser deterministico. Nao uses ordem arbitraria de `HashMap`.

## 5. Provisionar shares

Para prototipo:

- gera shares offline
- arranca cada no com a sua `privateShare`
- todos os nos conhecem a `groupPublicKey`

Para desenho serio:

- usa provisioning seguro ou DKG

Se gerares a chave completa centralmente e depois repartires shares, isso serve para testes, mas nao e o modelo ideal de seguranca.

## 6. Adicionar a dependencia da library

No `pom.xml`, adiciona a dependencia do repositorio:

- `com.weavechain:threshold-sig`

Confirma depois:

- versao da library
- classes publicas disponiveis
- formato esperado para shares, `R`, `Ri`, `k` e assinatura final

## 7. Criar mensagens novas para o protocolo threshold

Em `MessageType.java`, adiciona mensagens para os rounds do protocolo.

Exemplo:

- `THRESHOLD_QC_R1_REQUEST`
- `THRESHOLD_QC_R1_RESPONSE`
- `THRESHOLD_QC_R2_REQUEST`
- `THRESHOLD_QC_R2_RESPONSE`

Depois cria payloads novos em `src/messages`, por exemplo:

- `ThresholdQcRound1Request`
- `ThresholdQcRound1Response`
- `ThresholdQcRound2Request`
- `ThresholdQcRound2Response`

Cada payload deve transportar o contexto minimo da sessao:

- `sessionId`
- `blockHash`
- `view`
- `phase`
- `configVersion`
- dados do round atual

## 8. Criar um gestor de sessoes threshold

Cria uma package nova, por exemplo:

- `src/consensus/threshold`
- ou `src/crypto/threshold`

E classes do genero:

- `ThresholdSigningSession`
- `ThresholdSignatureCoordinator`
- `ThresholdSignatureParticipant`
- `ThresholdCryptoConfig`

Cada sessao deve guardar:

- `sessionId`
- bytes exatos a assinar
- conjunto de participantes
- respostas do round 1
- `R`
- `k`
- respostas do round 2
- timeout
- estado finalizada ou abortada

Sem isto, nao consegues lidar bem com:

- duplicados
- atrasos
- retries
- mensagens fora de ordem

## 9. Escolher quem coordena a assinatura

O mais natural e o lider da fase coordenar a threshold signature.

Razao:

- ja sabe quando atingiu quorum
- ja vai difundir a mensagem seguinte com o QC

Fluxo esperado:

1. o lider deteta quorum logico
2. o lider inicia a sessao threshold
3. os replicas enviam respostas de round 1 e round 2
4. o lider agrega a assinatura final
5. o lider cria o `QuorumCertificate`
6. o lider envia a mensagem HotStuff seguinte com esse QC

## 10. Integrar o protocolo threshold no HotStuffNode

Hoje o `HotStuffNode` faz isto:

1. conta votos em memoria
2. quando chega a quorum, cria `new QuorumCertificate(...)`
3. envia a fase seguinte

Com threshold signatures, o fluxo passa a ser:

1. contar votos como hoje
2. quando chega a quorum, construir os bytes canonicos do QC
3. iniciar uma sessao threshold
4. recolher respostas do protocolo
5. agregar a assinatura final
6. construir o `QuorumCertificate` com assinatura
7. so depois enviar a fase seguinte

Isto aplica-se a:

- `PREPARE -> PRE_COMMIT`
- `PRE_COMMIT -> COMMIT`
- `COMMIT -> DECIDE`

## 11. Manter a contagem de votos separada do certificado final

Nao mistures duas responsabilidades.

Deves ter:

- um mecanismo de recolha/contagem de votos no `HotStuffNode`
- um `QuorumCertificate` final como prova criptografica compacta

Ou seja:

- primeiro decides que ha quorum
- depois materializas esse quorum numa threshold signature

A assinatura threshold nao substitui a logica de quorum. Ela prova o resultado dessa logica.

## 12. Reescrever o QuorumCertificate

O `QuorumCertificate` final deve ser praticamente imutavel.

Deve ter:

- metadados do quorum
- assinatura threshold final
- eventualmente um metodo `verify(...)`

Nao deve ser o sitio onde vais acumulando votos ao longo do tempo.

## 13. Verificar QCs recebidos

Sempre que um no receber um `HotStuffMessage` com `qc`, deve:

1. reconstruir os bytes canonicos do QC
2. verificar a assinatura com a `groupPublicKey`
3. rejeitar o QC se a verificacao falhar

Isto deve acontecer antes de aceitar:

- `PRE_COMMIT`
- `COMMIT`
- `DECIDE`
- `NEW_VIEW`, se mais tarde quiseres justificar com QC assinado

## 14. Decidir o que fazer aos votos individuais

Tens duas opcoes.

Opcao simples:

- os votos servem apenas para o lider saber que atingiu quorum
- so o QC final tem prova criptografica forte

Opcao mais robusta:

- mantens autenticacao individual dos votos
- e depois produzes um QC threshold como prova compacta final

Para um projeto academico/prototipo, a opcao simples pode ser suficiente.

## 15. Nao threshold-signar ACKs

Os `ACK`s do `RetryLink` nao devem usar threshold signatures.

Motivos:

- nao representam aprovacao coletiva
- iam complicar brutalmente o protocolo
- nao trazem beneficio real

Threshold signatures devem existir apenas para objetos do tipo:

- `QuorumCertificate`
- certificados de `new-view`
- certificados de commit

## 16. Reformular a camada crypto

O `CryptoService` atual esta desenhado para:

- assinatura individual com `PrivateKey`
- verificacao com `PublicKey`

Se fores manter assinaturas individuais para outras partes do sistema:

- separa `NodeSignatureService`
- separa `ThresholdCertificateService`

Se fores usar threshold apenas nos QCs, nao forces a mesma interface a representar ambos os casos.

## 17. Estrutura recomendada das novas classes

Sugestao de classes novas:

- `ThresholdCryptoConfig`
- `ThresholdSignatureManager`
- `ThresholdSigningSession`
- `ThresholdRound1Request`
- `ThresholdRound1Response`
- `ThresholdRound2Request`
- `ThresholdRound2Response`
- `ThresholdQuorumCertificate`

Sugestao de responsabilidades:

- `ThresholdCryptoConfig`: chaves, shares, indices, `t`, `n`
- `ThresholdSignatureManager`: iniciar sessoes, despachar rounds, agregar assinatura final
- `ThresholdSigningSession`: guardar estado de uma sessao concreta
- mensagens `Round1/Round2`: transportar dados entre nos
- `ThresholdQuorumCertificate`: certificado final verificavel

## 18. Ordem de implementacao recomendada

Segue esta ordem para reduzir risco:

1. limpar codigo morto obvio
2. congelar `PerfectLink` e `RetryLink`
3. redesenhar `QuorumCertificate`
4. criar `ThresholdCryptoConfig`
5. adicionar tipos de mensagem e payloads novos
6. criar `ThresholdSignatureManager`
7. implementar sessoes e timeouts
8. integrar no `HotStuffNode`
9. adicionar verificacao obrigatoria de QC
10. so depois rever se ainda precisas de autenticacao individual extra

## 19. Testes que tens de escrever

Escreve testes para:

- QC valido com exatamente `t` participantes
- QC valido com mais de `t`
- falha com `t-1`
- round 1 duplicado nao conta duas vezes
- round 2 duplicado nao conta duas vezes
- resposta de participante nao autorizado e rejeitada
- QC adulterado falha verificacao
- QC com `view` errada falha verificacao
- QC com `phase` errada falha verificacao
- rececao de `PRE_COMMIT` com QC invalido e rejeitada
- rececao de `COMMIT` com QC invalido e rejeitada

## 20. Regra de desenho mais importante

Se o teu objetivo e `threshold signatures` so para certificados/quoruns, a regra correta e:

- quorum first
- threshold certificate second

Ou seja:

1. o lider observa que recebeu quorum suficiente
2. o lider pede a producao da assinatura threshold
3. a assinatura final vira um QC compacto
4. esse QC e o que acompanha as mensagens seguintes do protocolo

Nao uses threshold signatures para substituir o link layer.
