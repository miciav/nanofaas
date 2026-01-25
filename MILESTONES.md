# Piano di lavoro e issue

## Vincoli e assunzioni (MVP)

- Linguaggio: Java.
- Framework: Spring Boot con AOT/GraalVM native image.
- Control plane in **un solo pod**: API gateway, coda interna, scheduler.
- Scheduler su **thread dedicato** nel control plane.
- Funzioni eseguite in **pod separati** su Kubernetes.
- Observability: metriche Prometheus esposte dal control plane.
- Coda interna **in-memory** (nessuna durabilita' al riavvio).
- Retry automatico default 3, configurabile dall'utente; idempotenza a carico dell'utente.
- Performance come priorita' assoluta (latenza e cold start prima di tutto).

## Milestone 1 — Fondazioni repository e toolchain

- ISSUE-001: Inizializzare struttura moduli (control-plane, function-runtime, common)
  - Accettazione: layout cartelle creato e documentato.
- ISSUE-002: Configurare build Gradle con target Java e supporto native image
  - Accettazione: build locale JVM ok; task per native image definito.
- ISSUE-003: Containerizzazione base (Dockerfile) per control plane e runtime funzioni
  - Accettazione: immagini locali buildabili.
- ISSUE-004: Manifest K8s base (Namespace, ServiceAccount, RBAC minime)
  - Accettazione: apply su cluster di test senza errori.

## Milestone 2 — API Gateway + Contract di invocazione

- ISSUE-005: Definire API REST per invocazioni sync/async
  - Accettazione: OpenAPI con endpoint, payload e codici errore.
- ISSUE-006: Implementare gateway (routing, auth minima, rate-limit base)
  - Accettazione: invocazioni HTTP funzionanti in locale.
- ISSUE-007: Validazione input e idempotency key
  - Accettazione: richieste duplicate gestite correttamente.

## Milestone 3 — Coda interna e scheduler

- ISSUE-008: Implementare coda in-memory per funzione (bounded)
  - Accettazione: enqueue/dequeue per funzione con backpressure.
- ISSUE-009: Scheduler thread (timeout e gestione fallimenti, DLQ in-memory)
  - Accettazione: timeout configurabili e fallimenti tracciati con metriche base.
- ISSUE-010: Routing verso esecuzione locale/remota
  - Accettazione: decisione schedulazione tracciata e testata.

## Milestone 4 — Dispatcher Kubernetes

- ISSUE-011: Integrazione client K8s per creare Job/Pod
  - Accettazione: Job creato con template per funzione.
- ISSUE-012: Template runtime funzione (image, env, secrets, resources)
  - Accettazione: funzioni partono con config corretta.
- ISSUE-013: Gestione risultato (sync wait o callback async)
  - Accettazione: risposta sync con timeout e stato chiaro.

## Milestone 5 — Runtime funzioni (Java)

- ISSUE-014: Definire contratto funzione (HTTP/gRPC interno)
  - Accettazione: esempio funzione che riceve input e ritorna output.
- ISSUE-015: Runtime minimale Spring + GraalVM (fast cold-start)
  - Accettazione: immagine native e tempi di avvio misurati.
- ISSUE-016: Logging strutturato e propagazione trace-id
  - Accettazione: log coerenti e correlabili.

## Milestone 6 — Observability e affidabilita'

- ISSUE-017: Metriche Prometheus (queue depth, latency, success/fail)
  - Accettazione: endpoint `/actuator/prometheus` con metriche chiave.
- ISSUE-018: Health checks e graceful shutdown (drain queue)
  - Accettazione: readiness/liveness corretti in K8s.
- ISSUE-019: SLO e dashboard base (Grafana opzionale)
  - Accettazione: definizione SLO e metriche mappate.

## Milestone 7 — Documentazione e sample

- ISSUE-020: Quickstart (build, run locale, deploy k8s)
  - Accettazione: guida riproducibile.
- ISSUE-021: Esempio funzione (sync + async)
  - Accettazione: esempi funzionanti end-to-end.
