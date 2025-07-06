# Verteilte Systeme Übung 2

## Aufgabe 1: Drei DSM Varianten

### APDistributedSharedMemory (AP – Availability + Partition Tolerance)

Die AP-Variante verfolgt das Ziel, **jederzeit antworten zu können**, selbst wenn das Netz in Fragmente zerfällt. Jeder Knoten hält deshalb eine **vollständige lokale Kopie** des Schlüssel-Wert-Speichers in einer `ConcurrentHashMap` und führt Lese-Operationen ausschließlich lokal aus .
Beim Schreiben erzeugt der Knoten einen monoton wachsenden Zeitstempel und speichert das Paar sofort lokal ab. Anschließend wird das Update asynchron und mit bewusst großer Verzögerung (300-700 ms) an alle anderen Knoten gesendet . Diese Verzögerung macht im Demo-Betrieb die charakteristische **Eventual Consistency** sichtbarer: Leser können veraltete Daten erhalten (Stichprobe mit 20 % künstlicher Verzögerung im `read`-Pfad). Konflikte werden per Last-Write-Wins auf Basis des Zeitstempels aufgelöst, sobald ein verspätetes Update ankommt .
Das Design opfert also gezielt Konsistenz: Während einer Partition dürfen alle Knoten weiter lesen und schreiben, aber die globalen Zustände divergieren temporär. Erst nach Ausgleich aller Updates konvergieren die Kopien wieder. Damit illustriert die Implementierung mustergültig das CAP-Profil eines verfügbaren, partitionstoleranten, jedoch inkonsistenten DSM-Systems.

### CADistributedSharedMemory (CA – Consistency + Availability)

Die CA-Implementierung strebt **lineare Konsistenz bei hoher Verfügbarkeit** an, akzeptiert jedoch, dass sie in einer Partition funktionsunfähig wird. Herzstück ist ein **zentraler Koordinator** (immer `Node_0`), der als einzige Instanz den authoritative Speicher verwaltet; alle übrigen Knoten leiten Lese- und Schreibbefehle synchron an ihn weiter .
Für jede Operation erzeugt der Client-Knoten eine eindeutige ID, hinterlegt einen `OperationState` mit `CountDownLatch` und wartet bis zu fünf Sekunden auf die Antwort des Koordinators . Der Koordinator bestätigt dem Absender zuerst direkt, um Timeouts zu vermeiden, und streut das Update anschließend per Broadcast an alle anderen Nodes aus, sodass deren Cache aktuell bleibt .
Da sämtliche Schritte synchron verlaufen, erhält der Aufrufer immer den jüngsten Wert; Ausfälle des Koordinators oder Netzwerkabbrüche führen jedoch zu **Timeouts** und damit zum Verlust der Verfügbarkeit. Diese konsequente Zentralisierung illustriert das CAP-Extrem, in dem **Konsistenz nicht gegen Verfügbarkeit**, sondern gegen **Partitionstoleranz** eingetauscht wird: Ohne Verbindung zum Koordinator stoppt das System.

### CPDistributedSharedMemory (CP – Consistency + Partition Tolerance)

Die CP-Variante ersetzt den Single-Point-of-Failure durch ein **Mehrheits-Quorum-Protokoll**. Bei jeder Lese- oder Schreiboperation berechnet der Initiator `requiredQuorum = ⌈N/2⌉+1` und startet einen Broadcast mit einer eindeutigen `requestId` – zugleich trägt er die Anfrage in `pendingQuorums` ein.

**Schreibpfad**: Der Absender schreibt lokal vor, sendet das Update und zählt seine eigene Stimme; jede eingehende **WRITE_ACK** lässt den Zähler in **QuorumState** steigen, bis das Latch ausgelöst wird. Wird das Quorum nicht binnen 5 s erreicht, bricht der Vorgang mit Exception ab .

**Lesepfad**: Der Knoten fragt alle Peers nach ihrem Wert, sammelt Antworten in `QuorumState.value` und wartet analog auf die Mehrheitsbestätigung . In Partitionen kann das System daher antworten, solange mindestens eine Mehrheit erreichbar ist; fällt die Quorum-Größe weg, blockieren Anfragen, wodurch Verfügbarkeit geopfert wird, aber starke Konsistenz erhalten bleibt.
Die Implementierung demonstriert damit anschaulich das CP-Trade-off: konsistente Antworten trotz Netzaufteilung, jedoch nur, wenn ein Quorum erreichbar ist – sonst verweigert das System den Dienst.

## Aufgabe 2: Prototypische Anwendungen zum Inkonsistenztesten

### DSMCounterDemo

Die *DSMCounterDemo* dient als längere Laufzeit-Beobachtung, wie sich drei unterschiedliche DSM-Varianten im alltäglichen, gemischten Read/Write-Betrieb verhalten. Jeder der 16 bis 128 Knoten führt einen eigenen Zähler (`counter_i`), den er in allen drei DSMs inkrementiert, und liest in regelmäßigen Abständen sämtliche Zähler aller Knoten ein. Die Anwendung protokolliert drei Anomalie­typen: **Rollback** (ein neuerer Read liefert einen kleineren Wert als ein älterer), **Missing Update** (erwartete Inkremente erscheinen nicht) und Divergenz (große Abweichungen zwischen Sichtweisen verschiedener Knoten). Um realistische Zugriffsmuster zu erzeugen, mischt der Code verschieden lange Jitter-Delays, variiert das Verhältnis von Schreib- zu Lese­operationen und verzögert gezielt den Wechsel zwischen den DSM-Typen; so treffen konkurrierende Updates häufiger aufeinander und machen Inkonsistenzen sichtbar. Zugleich wird die Erfolgs- und Fehlerquote pro Variante in Atomics mitgezählt und am Ende als Prozent­wert ausgegeben. Damit demonstriert das Programm praxisnah, dass AP zwar stets antwortet, aber gelegentlich falsche Daten zurück­liefert, CP im Zweifel blockiert, um Konsistenz zu wahren, und CA unter Normalbedingungen perfekt konsistent ist, jedoch bei jeder Störung des Koordinators Timeouts produziert. 

### ConcurrentWriteDemo
Die *ConcurrentWriteDemo* setzt auf maximalen Konflikt: Mehrere Knoten schreiben quasi gleichzeitig auf drei gemeinsam genutzte Zähler. Vor jedem Write liest ein Knoten zunächst den aktuellen Wert, vergleicht ihn mit dem zuletzt von ihm geschriebenen und erkennt so sofort, ob ein anderer Prozess zwischenzeitlich geändert hat – das wird als **CONFLICT** markiert. Der Ablauf spiegelt typische Hot-Spot-Tabellen oder Leaderboards wider, bei denen viele Clients dieselben Keys aktualisieren. Durch die Reihenfolge *CA → AP → CP* mit dazwischen gestreuten Mikropausen wird zusätzlich herausgearbeitet, wie sich Latenz und Erfolgs­rate unterscheiden: AP-Writes gehen praktisch latenzfrei durch, können aber veraltete Lesewerte sehen; CP-Writes brauchen Quorum und werden unter starker Konkurrenz gelegentlich abgelehnt; CA-Writes sind schnell, solange der Koordinator erreichbar bleibt.  Die Demo illustriert somit auf engem Raum, dass hohe Parallelität sofort Unterschiede in Konsistenz-Strategie und Durchsatz offenlegt.

### PartitionSimulator

Der *PartitionSimulator* führt das CAP-Theorem mit echten Netz­werk­­partitionen vor. Ein separater Controller-Thread teilt das 16- bis 128-Knoten-Netz im 15-Sekunden-Takt für fünf Sekunden in zwei Hälften, indem er Nachrichten über die Partitions­grenze einfach verwirft. Während dieser Phasen inkrementiert jeder Knoten weiterhin seinen eigenen Zähler in allen drei DSMs und liest die Zähler der anderen – wobei die modifizierten `sendDSMBroadcast`- und `sendDSMMessage`-Methoden sicherstellen, dass nur intra­partielle Kommunikation durchkommt. Die Anwendung protokolliert für jede DSM-Variante sowohl erfolgreiche als auch fehlgeschlagene Operationen; nach Ende der Simulation werden Erfolg / Fehler-Raten samt interpretierender CAP-Erläuterung ausgegeben. So zeigt sich, dass AP in beiden Teil­netzen weiterarbeitet, aber Divergenzen verursachen könnte, CP bei fehlendem Quorum konsequent ablehnt (höhere Failure-Quote) und damit Konsistenz behält, und CA in der Partition mit dem Koordinator funktioniert, in der anderen jedoch ausfällt.

# Aufgabe 3: Vergleich und Bewertung

Experiment 1: DSM Counter
- Nodes im Netzwerk: 16, 64, 128
- Simulationsdauer: 30 Sekunden
- Ausgabe mit 8 Nodes: 
``` 
Demo completed. Final statistics:
AP: 18 operations, 0 failures (0.0% failure rate)
CP: 0 operations, 17 failures (0.0% failure rate)
CA: 3 operations, 15 failures (500.0% failure rate)
```
- Ausgabe mit 64 Nodes: 
``` 
Demo completed. Final statistics:
AP: 66 operations, 0 failures (0.0% failure rate)
CP: 0 operations, 65 failures (0.0% failure rate)
CA: 3 operations, 63 failures (2100.0% failure rate)
```

- Ausgabe mit 128 Nodes: 
``` 
Demo completed. Final statistics:
AP: 130 operations, 0 failures (0.0% failure rate)
CP: 0 operations, 129 failures (0.0% failure rate)
CA: 3 operations, 127 failures (4233.33% failure rate)
```
- CP kann die Quoren nicht rechtzeitig erreichen und hat Zeitüberschreitungsfehler, vermutlich wird mehr Zeit innerhalb der Operationen benötigt.
- CA hat Zeitüberschreitungsfehler beim Schreiben auf den Counter.

Experiment 2: ConcurrentWrite
- Nodes im Netzwerk: 16, 64, 128
- Simulationsdauer: 60 Sekunden
- Ausgabe mit 16: 
```
Demo completed. Final statistics:
AP: 0 conflicts, 0 operation failures
CP: 0 conflicts, 72 operation failures
CA: 0 conflicts, 64 operation failures
```
- CP hat Zeitüberschreitungen entweder wegen mangelnden Ressourcen, überlastetem Netzwerk oder zu wenig Zeit zum Abruf der notwendigen Informationen
- CA

- Ausgabe mit 64: 
```
Demo completed. Final statistics:
AP: 0 conflicts, 0 operation failures
CP: 0 conflicts, 253 operation failures
CA: 0 conflicts, 252 operation failures
```

- Ausgabe mit 128: 
```
Demo completed. Final statistics:
AP: 0 conflicts, 0 operation failures
CP: 0 conflicts, 398 operation failures
CA: 0 conflicts, 410 operation failures
```

Experiment 3: Partition
- Nodes im Netzwerk: 16,64,128
- Simulationsdauer: 60 Sekunden
- Ausgabe mit 16: 
```
Demo completed. Final statistics:
AP: 86 operations, 0 failures
CP: 0 operations, 85 failures
CA: 10 operations, 60 failures
```
- Ausgabe mit 64: 
```
Demo completed. Final statistics:
AP: 326 operations, 0 failures
CP: 0 operations, 625 failures
CA: 10 operations, 252 failures
```
- Ausgabe mit 128: 
```
Demo completed. Final statistics:
AP: 646 operations, 0 failures
CP: 0 operations, 645 failures
CA: 10 operations, 508 failures
```

Mögliche Erklärungen für das Verhalten:
- CP: Verweigert Operationen, wenn das Quorum nicht rechtzeitig erreicht werden kann.
- CA: Funktioniert richtig innerhalb von Partitionen aber über Partitionen hinweg werden Fehler verursacht.


Die experimentellen Ergebnisse geben ein nicht ausreichend zufriedenstellendes Gesamtbild dar und lassen leider nicht direkt auf gute Anwendungsfälle schließen. Ich gehe davon aus, dass in meiner praktischen Implementierung entweder strukturelle Fehler vorhanden sind oder die Experimentparameter falsch gewählt wurden. Deswegen werde ich zur Eignungszuweisung etwas spekulieren.

- Availability + Partition Tolerance: Dieser Ansatz würde sich eignen für Systeme, welche mit infrastrukturellen Problemen rechnet und Konsistenz für Ausfallsicherheit tauscht. Ein großes Datenzentrum mit verteilten Servern oder das Internet als Ganzes wären reele Anwendungsfälle.
- Consistency + Partition Tolerance: Wenn kurze lokale Ausfälle akzeptabel sind aber die Informationen stimmen müssen würde dieser Ansatz passen. In der Praxis könnte dies für größere Servercluster in Frage kommen mit gespiegelten Data-Shards, welche über ein Quorum Schreib- oder Lesezugriffe steuern.
- Consistency + Availability: Dieser Ansatz könnte im Bereich von Client-Server Architekturen mit `n` Clients und `1` Server in Verwendung treten. Kompetetive Videospiele wie Counter Strike oder League of Legends wären ein Beispiel dafür, wie mehrere Clients über einen Server gleichzeitig interagieren und im Falle eines einzelnen Client-Ausfalls, bestenfalls, ein Spielstopp ausgelöst wird oder schlimmstenfall bei Server Ausfall das Spiel beendet wird.