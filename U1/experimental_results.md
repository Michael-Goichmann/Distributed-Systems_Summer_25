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
- CPU: AMD Ryzen 7 3700X 8-Core 16-Threads @ ~ 4.15 GHz
- RAM: 32GB
- Rest vermutlich egal

## Command to launch automated experiments
The parameters are in order: n = number of processes, k = number of no fireworks
or until termination, p = inital probability, m = number of repetition per experiment
```
python U1/src_task_1/task_1_launcher.py "2" 3 0.5 30 U1/src_task_1/experiment_results.csv
```

or to simply run it once
```
python U1/src_task_1/task_1_launcher.py "2" 3 0.5 1 U1/src_task_1/experiment_results.csv
```

## Experiments

| Number Processes | Tokenrunden | Gesamt Multicasts | Rundenzeit (Min, Median, Max) in ms|
| :--------------: | :-----------: | :-----------------: | :-----------------------------: |
| 2|5|2|(211, 400, 595)|
| 4|6|4|(527, 799, 1065)|
| 8|7|8|(1.162, 1.587, 1.939)|
| 16|5|10|(3.089, 3.353, 3.473)|
| 32|8|42|(5.866, 6.130, 6.562)|
| 64|8|61|(12.349, 13.044, 14.001)|
| 128|12|128|(24.680, 25.632, 26.897)|
| 256|11|260|(50.432, 51.591, 52.630)|
| 512|14|525|(100.998, 103.193, 106.381)|
| 1024|14|1043|(203.715, 207.358, 215871)|

Aus Zeitgründen wurde der Limittest nach 1024 Prozessen beendet. Bei n = 512
wurden vom System ~ 3.5GB RAM beansprucht, bei n = 1024 ~ 7.1GB RAM. Bereits 
n = 2048 haben die CPU überlastet und den Rechner ins Schwanken gebracht.
Bis n= 16 wurden Experimente 30 mal wiederholt. Danach nur einmalig.


# Experimente Task 2: Ein Feuerwerk an UDP-Nachrichten (Verteilt)
Code umgestellt, um über die IP zu kommunizieren. Leider nicht lokal zum laufen bekommen.
Versucht wurde: Firewall IPv4 Kommunikation zu erlauben, UDP Ports im Netzwerk
frei zu bekommen. Pings gingen, aber Nachrichten versenden hat aus irgendwelchen
Gründen nicht funktioniert.

# Experimente Task 3: Ein simuliertes Feuerwerk
| Number Processes | Tokenrunden | Gesamt Multicasts | Rundenzeit (Min, Median, Max) in ms|
| :--------------: | :-----------: | :-----------------: | :-----------------------------: |
|2|7|2|0.45, 16.98, 115.97|
|4|8|5|0.61, 0.87, 1.36|
|8|8|5|0.74, 1.63, 3.19|
|16|7|17|1.27, 3.59, 7.88|
|32|9|23|2.29, 4.78, 15.76|
|64|9|61|3.98, 17.84, 55.41|
|128|10|125|4.59, 91.56, 551.93|
|256|16|262|6.62, 216.33, 1_427.59|
|512|14|517|12.34, 880.09, 5_570.06|
|1024|11|1016|27.60, 4_609.57, 24_039.36|
|2048|17|2014|52.51, 9_673.75, 80_081.93|
|2048|14|4211|106.33, 54_626.54, 39_6664.43|


