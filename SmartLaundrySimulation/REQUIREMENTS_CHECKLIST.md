# Requirement checklist (see the report for explanations; assumption numbers match the report)

## Basic
| Requirement | Where | Evidence |
|---|---|---|
| 6 washers, 4 dryers, 2 kiosks, 50 customers | `NUM_*` constants | every run |
| Customer = thread, arrivals 0-3 s | `Arrival-Spawner`, `Customer-NN` threads | thread name on every log line |
| Wash 4-6 s, wait if all busy | `washStage`, `washerGate` (FifoGate, 6) | peak 6/6 |
| Dry 3-5 s, wait for dryer | `dryStage`, `dryerSem` (fair Semaphore, 4) | peak 4/4 |
| Payment 1-2 s, 2 kiosks | `payStage`, `payGate` (FifoGate, 2) | |

## Additional
| Requirement | Where | Evidence |
|---|---|---|
| Mutual exclusion (Semaphore / ReentrantLock / synchronized) | `FifoGate`, `Semaphore`, `lock`, `log()` | `ConsoleRunner` self-check: 0 double-use |
| 5 % washer failure mid-cycle, wait + retry | `washStage` (Normal and Congested) | log "FAILED mid-cycle" |
| 5 % kiosk failure, retry after 2 s | `payStage`, `KIOSK_FAIL_RETRY` | log "FAILED during my payment" |
| Statistics: served, average time, max washers/dryers | `served`, `totalTimeMs`, `peak*` (atomics + CAS) | final log + results window |
| Concurrency visible, thread named in output | `log()` prefixes the thread name | console / GUI log |
| Run time about 60 s | Assumption A3: 75-110 s (arrivals alone ~73 s) | stated in report |

## Bonus
| Requirement | Where | Evidence |
|---|---|---|
| Both kiosks broken all day | `Scenario.CONGESTED` | |
| Owner called after 30 at payment queue | `payStage` -> `callOwner` | log "30 customers are stuck ... CALLING THE OWNER" |
| GUI visualisation | Swing dashboard | screenshots in report |

## Extras beyond the brief
- `Extra: Incidents` scenario (owner repairs of washers and kiosks on demand).
- Extra statistics: average wait per stage, utilisation, throughput; saved to `simulation_results.txt`; shown in the results window.
- `ConsoleRunner`: headless runs with an automatic safety self-check (repeatable test).
- Pause, 1x/2x/4x speed with a simulation clock (results identical at every speed).
