# Experimente Task 1: Ein Feuerwerk an UDP-Nachrichten
In dieser Datei werden die Ergebnisse für die erste Teilaufgabe des Übungsblattes
gesammelt.
Die Aufgabe umfasst hierbei eine vollständig lokale Umsetzung eines logischen Rings,
welche ein Token im Kreis per UDP als Pseudo-Broadcast bzw. Multicast verschickt.

Im Rahmen der Experimente wird bestimmt:
- Wie viele Prozesse die lokale Maschine maximal fehlerfrei ausführen kann
- Statistische Informationen in Abhängigkeit von n (Zahl der Prozesse) wie:
    - Gesamtzahl der Tokenrunden
    - Gesamtzahl der gesendeten Multicasts
    - Minimale, mittlere und maximale Rundenzeit

## Hardware Information
Der vollständigkeit Halber wir hier kurz die Hardware aufgelistet mit der diese
Teilaufgabe durchgeführt wurde.
- Mainboard: MSI B450 Gaming Plus Max
- CPU: AMD Ryzen 7 3700X 8-Core 16-Threads
- RAM: 32GB
- Rest vermutlich egal

## Command to launch automated experiments
The parameters are in order: n = number of processes, k = number of no fireworks
or until termination, p = inital probability, m = number of repetition per experiment
```
python U1/task_1_launcher.py "2" 3 0.5 30 U1/experiment_results.csv
```

or to simply run it once
```
python U1/task_1_launcher.py "2" 3 0.5 1 U1/experiment_results.csv
```

## Experiments

| Number Processes | Tokenrunden | Gesamt Multicasts | Rundenzeit (Min, Median, Max) |
| :--------------: | :-----------: | :-----------------: | :-----------------------------: |
| 2||||
| 4||||
| 6||||
| 8||||
| 10||||
| 12||||



