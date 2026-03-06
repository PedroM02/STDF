```
src/main/java/com/yourorg/depchain/
│
├── common/
│   ├── ProcessId.java
│   ├── Address.java
│   ├── NodeConfig.java
│   ├── Membership.java
│   └── Constants.java
│
├── transport/
│   ├── UdpTransport.java
│   └── UdpReceiver.java
│
├── crypto/
│   ├── CryptoService.java
│   ├── SignatureUtils.java
│   ├── KeyStoreLoader.java
│   └── HashUtils.java
│
├── messages/
│   ├── MessageType.java
│   ├── MessageId.java
│   ├── Envelope.java
│   ├── AckMessage.java
│   └── ProtocolMessage.java
│
├── links/
│   ├── LinkReceiver.java
│   ├── RetryLink.java
│   ├── PerfectLink.java
│   └── AuthenticatedPerfectLink.java
│
├── consensus/
│   ├── hotstuff/
│   │   ├── HotStuffNode.java
│   │   ├── HotStuffMessage.java
│   │   ├── ViewNumber.java
│   │   ├── QuorumCertificate.java
│   │   ├── Block.java
│   │   └── Pacemaker.java
│   │
│   └── model/
│       ├── Vote.java
│       └── Proposal.java
│
├── blockchain/
│   ├── BlockchainService.java
│   └── InMemoryLedger.java
│
├── client/
│   ├── ClientLibrary.java
│   └── ClientRequest.java
│
├── node/
│   ├── Node.java
│   ├── NodeRuntime.java
│   └── Main.java
│
└── testsupport/
    ├── NetworkEmulator.java
    ├── FaultInjector.java
    └── TestUtils.java
```

## `common/`

Tudo o que é partilhado.

### `ProcessId`
Representa o identificador lógico de um nó.

### `Address`
IP + porto.

### `NodeConfig`
Configuração de um nó:

- id
- endereço
- caminho para chave privada
- chaves públicas dos outros

### `Membership`
Lista de todos os membros da blockchain, como o enunciado assume.

---

## `transport/`
Camada mais baixa.

### `UdpTransport`
Faz só:

- `send(Address, byte[])`
- regista callback de receção

Não põe semântica nenhuma em cima de UDP.

---

## `crypto/`
Tudo o que é criptografia.

### `CryptoService`
Interface principal para:

- assinar
- verificar
- fazer hash

Se usarem assinaturas digitais:

- `sign(senderPrivateKey, bytes)`
- `verify(senderPublicKey, bytes, signature)`

Isto encaixa bem com a PKI que o projeto assume.

---

## `messages/`

Aqui defines o formato base de rede.

### `MessageId`

Algo como:

```
record MessageId(int senderId, long sequenceNumber) {}
```

### `MessageType`
Exemplo:
```
DATA, ACK
```

Mais tarde podes acrescentar:
```
NEW_VIEW, PREPARE, PRE_COMMIT, COMMIT, DECIDE
```

### `Envelope`
O contentor comum que circula na rede.

Exemplo:
```
class Envelope {
    MessageId messageId;
    int senderId;
    int receiverId;
    MessageType type;
    byte[] payload;
    byte[] signature;
}
```

Isto é ótimo porque depois o HotStuff só mete o seu conteúdo em `payload`.

---

## links/

### `LinkReceiver`

Uma interface para callbacks:
```
public interface LinkReceiver {
    void onReceive(int src, byte[] payload);
}
```

Ou, se quiseres manter o envelope inteiro:
```
void onReceive(Envelope envelope);
```

### `RetryLink`
Responsabilidade:
- receber um `Envelope`
- mandar por UDP
- guardar como pendente
- reenviar até receber ACK

Estado típico:
```
Map<MessageId, PendingMessage> pending;
AtomicLong nextSeq;
ScheduledExecutorService scheduler;
```

Funções:
- `send(dest, payload)`
- `handleIncomingRaw(...)`
- `handleAck(...)`
- `retransmitPending()`

### `PerfectLink`
Responsabilidade:
- usar o Retry Link por baixo
- manter `delivered`
- garantir entrega única à camada superior

Estado:
```
Set<MessageId> delivered;
```

Lógica:
- quando recebe uma `DATA`, se ainda não foi entregue:
    - entrega à camada acima
    - marca como entregue
- se já tinha sido entregue:
    - ignora

### `AuthenticatedPerfectLink`
Responsabilidade:
- assinar antes de enviar
- verificar antes de aceitar
- só entregar mensagens autenticadas

Ele pode usar o `PerfectLink` por baixo.

Lógica:
- no `send()`:
    - cria envelope
    - assina os campos relevantes
    - passa ao PerfectLink
- no `receive()`:
    - verifica assinatura com a chave pública do sender
    - se inválida, descarta
    - se válida, entrega à camada superior
