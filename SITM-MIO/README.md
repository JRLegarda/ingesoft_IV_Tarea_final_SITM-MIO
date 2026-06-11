# SITM-MIO Distributed Analytics System

Sistema para calcular la velocidad promedio por ruta y mes a partir de datagramas del SITM-MIO. La version principal del repositorio es distribuida y usa ZeroC Ice con un esquema Master-Worker. Tambien se dejo separado un nucleo de procesamiento que sirve como base para las versiones monolitica y monolitica concurrente pedidas por el enunciado.

## Estructura

- `MasterNode`: coordina el procesamiento distribuido, divide el archivo en chunks y agrega resultados.
- `WorkerNode`: expone el servicio remoto Ice, lee datagramas y calcula acumulados de velocidad por ruta-mes.
- `MasterNode/src/main/resources/lines-241-ActiveGT.csv`: catalogo usado para generar el CSV final.
- `data/datagrams-MiniPilot.csv`: dataset local para prueba rapida.
- `/opt/sitm-mio/datagrams4Pilot.csv`: dataset esperado para validacion final en los nodos de laboratorio.

## Prerrequisitos

- Java JDK 17.
- Gradle wrapper incluido en cada modulo.
- El archivo pesado de datagramas solo debe existir en el Master. Los Workers no necesitan una copia local del CSV: reciben los bloques por demanda desde el Master usando Ice sobre la red local.

## Construccion

Worker:

```bash
cd WorkerNode
./gradlew shadowJar
```

Master:

```bash
cd MasterNode
./gradlew shadowJar
```

En Windows PowerShell, usar `.\gradlew.bat shadowJar`.

## Ejecucion distribuida basica

Primero iniciar uno o mas workers. Luego iniciar el master.

Worker:

```bash
cd WorkerNode
./gradlew shadowJar
java -jar build/libs/worker-node.jar --Ice.Config=src/main/resources/config.worker
```

Master:

```bash
cd MasterNode
./gradlew shadowJar
java -jar build/libs/master-node.jar --Ice.Config=src/main/resources/config.master
```

El master mostrara un menu. Para prueba rapida local, seleccionar `../data/datagrams-MiniPilot.csv`. Para validacion final, usar `/opt/sitm-mio/datagrams4Pilot.csv` en el equipo Master.

En el laboratorio, todos los equipos estan conectados fisicamente por switches. Por eso el Master puede leer el CSV de 70 GB desde su disco y servir rafagas de bytes de 10 MB a los Workers por la red local, evitando clonar el archivo en cada maquina.

## Configurar varios workers

En `MasterNode/src/main/resources/config.master`:

```properties
Master.WorkerHosts=localhost:10000,10.147.17.104:10000,10.147.17.106:10000
Master.WorkerTimeoutMs=600000
Master.ChunkOverlapBytes=1048576
Master.ActiveLinesFile=lines-241-ActiveGT.csv
Master.OutputCsv=route_month_speeds.csv
Ice.MessageSizeMax=32768
FileProviderAdapter.Endpoints=tcp -h localhost -p 11000 -t 600000
```

`Master.WorkerHosts` acepta entradas `host:puerto` separadas por coma o punto y coma. El master convierte cada entrada en un proxy Ice `SimpleWorker:tcp -h host -p puerto -t timeout`.

Para maquinas distintas, cambiar `FileProviderAdapter.Endpoints` para publicar la IP real del Master en la red del laboratorio:

```properties
FileProviderAdapter.Endpoints=tcp -h 10.147.17.103 -p 11000 -t 600000
```

Esa IP debe ser alcanzable desde todos los Workers. Si se deja `localhost`, solo funcionara cuando Master y Worker corran en la misma maquina.

Si los workers estan en maquinas distintas, todos pueden usar el puerto `10000`:

```properties
WorkerAdapter.Endpoints=tcp -p 10000
Ice.MessageSizeMax=32768
```

Si se ejecutan varios workers en una sola maquina, cada worker necesita un puerto distinto:

```bash
cd WorkerNode
java -jar build/libs/worker-node.jar --Ice.Config=src/main/resources/config.worker
java -jar build/libs/worker-node.jar --Ice.Config=src/main/resources/config.worker.10001
java -jar build/libs/worker-node.jar --Ice.Config=src/main/resources/config.worker.10002
```

Y el master debe apuntar a esos puertos:

```properties
Master.WorkerHosts=localhost:10000,localhost:10001,localhost:10002
```

## Salida

El master genera un CSV con matriz ruta-mes:

```text
route_id,route_short_name,route_description,2018-05,2018-06,...
131,T31,Terminal Paso del Comercio - Universidades,...
```

El promedio se calcula con suma ponderada (`speedSum / count`), no como promedio de promedios. Si algun chunk falla, el master reporta "analisis completado con errores" y no genera el CSV como valido.

## Separacion para versiones monoliticas

El nucleo reutilizable esta en:

- `WorkerNode/src/main/java/org/example/core/ChunkProcessor.java`
- `WorkerNode/src/main/java/org/example/core/ChunkResult.java`
- `WorkerNode/src/main/java/org/example/core/RouteMonthResult.java`
- `WorkerNode/src/main/java/org/example/engine/DatagramReader.java`
- `WorkerNode/src/main/java/org/example/engine/SpeedCalculator.java`

Estas clases no dependen del contrato remoto `Demo.TaskResult` ni de una llamada Ice. Por eso sirven como base para construir, en entregas separadas, las otras dos versiones pedidas por el enunciado:

- Version monolitica: procesa todo el archivo en un solo segmento.
- Version monolitica concurrente: divide el archivo en chunks locales y usa `ThreadPool`.
- Version distribuida: cada worker procesa un rango asignado por el Master y lee sus bytes por demanda desde `FileProvider.readChunk(...)`.

La version distribuida usa ese nucleo asi:

- `WorkerNode/src/main/java/org/example/engine/ProcessingEngine.java` actua como adaptador Ice.
- `ProcessingEngine` llama a `ChunkProcessor`.
- `ProcessingEngine` convierte `ChunkResult` a `Demo.TaskResult`.

Para implementar la version monolitica, se debe crear un `main` que llame:

```java
ChunkResult result = new ChunkProcessor().process(filePath, 0, file.length(), 0);
```

Para implementar la version monolitica concurrente, se debe dividir el archivo en chunks locales, crear un `ExecutorService` y ejecutar varias llamadas a `ChunkProcessor.process(...)`, agregando luego los `RouteMonthResult` con suma ponderada (`speedSum / count`).

## Patrones y estilos usados

**Master-Worker**

- Evidencia: `MasterNode/src/main/java/org/example/scheduler/JobScheduler.java` divide el archivo en chunks; `MasterNode/src/main/java/org/example/controller/MasterController.java` despacha cada chunk a un worker; `WorkerNode/src/main/java/Demo/WorkerI.java` recibe la llamada remota.
- Por que: permite escalar agregando nodos de procesamiento y mejora performance sobre archivos grandes.

**Proxy remoto / RPC con ZeroC Ice**

- Evidencia: `MasterController` construye proxies `WorkerPrx`; `Compute.ice` define `Worker.processDatagramLog` y `FileProvider.readChunk`.
- Por que: desacopla el master de la implementacion concreta del worker y permite ubicar workers en otras maquinas.

**Work-Stealing**

- Evidencia: `MasterController.executeWorkStealing(...)` usa una `ConcurrentLinkedQueue` comun de chunks.
- Por que: cada worker toma un nuevo rango cuando termina el anterior, lo que balancea mejor si los equipos del laboratorio tienen capacidades distintas.

**Facade**

- Evidencia: `MasterNode/src/main/java/org/example/facade/SystemFacade.java`.
- Por que: ofrece una entrada simple (`startAnalysis`) y oculta la coordinacion interna del scheduler.

**Producer-Consumer**

- Evidencia: `DatagramReader` produce datagramas en una `BlockingQueue`; `SpeedCalculator` los consume.
- Por que: separa lectura de archivo y calculo, permite backpressure y evita cargar todo el archivo en memoria.

**Thread Pool**

- Evidencia: `JobScheduler` usa `Executors.newFixedThreadPool(...)` segun la cantidad de workers disponibles.
- Por que: limita concurrencia, evita saturar un worker unico y deja una base clara para implementar la version monolitica concurrente con el mismo `ChunkProcessor`.

## Consideraciones de correctitud

- Los chunks se dividen por byte, pero el reader ajusta el inicio para no partir una linea CSV.
- Hay solapamiento configurable (`Master.ChunkOverlapBytes`) para recuperar contexto de posiciones previas por bus entre chunks.
- El worker lee los bytes con `FileProvider.readChunk(offset, size)` desde el Master y solo contabiliza resultados desde `calculationStartOffset`, evitando duplicar velocidades del solapamiento.
- El calculo final se agrega por `lineId` y mes `yyyy-MM`.
- La salida funcional no calcula top de arcos ni resultados por variante/orientacion. Esas metricas pertenecian al enfoque anterior y se retiraron para mantener el alcance del enunciado actual.

## Validacion experimental sugerida

Registrar una tabla como esta:

```text
Archivo              Workers/Hilos    Tiempo total    Throughput    Speedup
MiniPilot            Monolitico        ...             ...           1.00x
MiniPilot            ThreadPool 4      ...             ...           ...
MiniPilot            1 worker          ...             ...           ...
MiniPilot            2 workers         ...             ...           ...
MiniPilot            3 workers         ...             ...           ...
datagrams4Pilot      1 worker          ...             ...           ...
datagrams4Pilot      2 workers         ...             ...           ...
datagrams4Pilot      3 workers         ...             ...           ...
```

Para pruebas distribuidas reales, confirmar que cada worker puede conectarse al endpoint `FileProviderAdapter` del Master por la red local.
