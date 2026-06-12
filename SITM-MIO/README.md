# SITM-MIO - Version distribuida

Esta carpeta contiene la version distribuida del proyecto final de Ingenieria de Software IV para el procesamiento de datagramas del SITM-MIO.

El objetivo de esta version es calcular la velocidad promedio por ruta activa y por mes usando varios equipos o procesos conectados por red. La solucion usa ZeroC Ice y sigue un esquema Master-Worker: el Master coordina el trabajo, conserva el archivo de datagramas y genera el CSV final; los Workers reciben rangos de bytes, procesan datagramas y devuelven acumulados parciales.

## Relacion con el enunciado

El enunciado pide comparar varias formas de resolver el mismo calculo:

- Version monolitica: `../SITM-MIO-Monolithic`.
- Version concurrente local con `ThreadPool`: `../SITM-MIO-ThreadPool`.
- Version distribuida con ZeroC Ice: esta carpeta.

Esta version es la que permite ejecutar el procesamiento en varios nodos. A diferencia de la version `ThreadPool`, aqui los workers pueden estar en maquinas distintas y se comunican con el Master mediante llamadas remotas Ice.

## Estructura del proyecto

```text
SITM-MIO/
+-- MasterNode/
|   +-- src/main/java/Demo/MasterNode.java
|   +-- src/main/java/Demo/FileProviderI.java
|   +-- src/main/java/org/example/controller/MasterController.java
|   +-- src/main/java/org/example/scheduler/JobScheduler.java
|   +-- src/main/java/org/example/report/RouteMonthCsvWriter.java
|   +-- src/main/resources/config.master
|   +-- src/main/resources/lines-241-ActiveGT.csv
|   +-- src/main/slice/Compute.ice
+-- WorkerNode/
    +-- src/main/java/Demo/WorkerNode.java
    +-- src/main/java/Demo/WorkerI.java
    +-- src/main/java/org/example/engine/ProcessingEngine.java
    +-- src/main/java/org/example/core/ChunkProcessor.java
    +-- src/main/java/org/example/engine/DatagramReader.java
    +-- src/main/java/org/example/engine/SpeedCalculator.java
    +-- src/main/resources/config.worker
    +-- src/main/slice/Compute.ice
```

## Componentes principales

**MasterNode**

El Master es el coordinador. Sus responsabilidades son:

- Leer la configuracion de `config.master`.
- Descubrir y conectar los workers declarados en `Master.WorkerHosts`.
- Publicar el servicio remoto `FileProvider`, usado por los workers para leer partes del archivo.
- Dividir el archivo de datagramas en chunks.
- Despachar chunks a los workers con balanceo dinamico.
- Reintentar chunks fallidos hasta un maximo configurado en codigo.
- Agregar los resultados parciales.
- Escribir el CSV final `route_month_speeds.csv`.

Las clases clave son:

- `Demo.MasterNode`: punto de entrada del Master.
- `Demo.FileProviderI`: implementacion de lectura remota del archivo.
- `org.example.controller.MasterController`: conexion con workers y ejecucion distribuida.
- `org.example.scheduler.JobScheduler`: planificacion de chunks y generacion del reporte final.
- `org.example.report.RouteMonthCsvWriter`: agregacion final y escritura de la matriz ruta-mes.

**WorkerNode**

Cada Worker es un proceso remoto que espera tareas del Master. Sus responsabilidades son:

- Exponer el objeto Ice `SimpleWorker` en el puerto configurado.
- Recibir un rango de bytes a procesar.
- Leer los bytes del archivo llamando al `FileProvider` remoto del Master.
- Parsear datagramas, calcular velocidades validas y agrupar resultados por ruta-mes.
- Devolver al Master `speedSum` y `count` por cada ruta-mes, junto con metricas del chunk.

Las clases clave son:

- `Demo.WorkerNode`: punto de entrada del Worker.
- `Demo.WorkerI`: adaptador Ice que recibe la llamada remota.
- `org.example.engine.ProcessingEngine`: conecta la llamada remota con el procesamiento local.
- `org.example.core.ChunkProcessor`: ejecuta el lector y calculador de velocidades.
- `org.example.engine.DatagramReader`: lee el rango remoto por bloques y produce datagramas.
- `org.example.engine.SpeedCalculator`: consume datagramas y calcula velocidades.

## Contrato remoto Ice

El contrato esta definido en `src/main/slice/Compute.ice` tanto en Master como en Worker.

```slice
interface FileProvider {
    long fileSize();
    ByteSeq readChunk(long offset, int size);
};

interface Worker {
    TaskResult processDatagramLog(
        FileProvider* fileProvider,
        long startOffset,
        long endOffset,
        long calculationStartOffset,
        int remoteReadSizeBytes,
        bool verbose
    );
};
```

Los tipos principales son:

- `FileProvider`: servicio publicado por el Master para que los Workers lean bytes del archivo.
- `Worker`: servicio publicado por cada Worker para procesar un chunk.
- `TaskResult`: respuesta de un Worker con nombre, tiempo, datagramas procesados, velocidades calculadas y lista de resultados ruta-mes.
- `RouteMonthSpeed`: acumulado parcial por `lineId` y mes, con `speedSum` y `count`.

## Flujo de ejecucion distribuida

1. Se inician uno o mas `WorkerNode`.
2. Cada Worker publica `SimpleWorker` en el endpoint configurado, normalmente `tcp -p 10000`.
3. Se inicia `MasterNode`.
4. El Master lee `Master.WorkerHosts` y construye proxies Ice hacia los Workers.
5. El Master pide al usuario seleccionar el archivo de datagramas.
6. El Master publica `FileProviderAdapter`, que expone el archivo mediante `FileProvider.readChunk`.
7. `JobScheduler` consulta el tamano del archivo con `fileProvider.fileSize()`.
8. `JobScheduler` divide el archivo en chunks de bytes.
9. `MasterController` coloca los chunks en una cola concurrente.
10. Cada hilo asociado a un Worker toma chunks de la cola y llama `worker.processDatagramLog(...)`.
11. El Worker lee los bytes necesarios desde el Master, procesa el rango y devuelve acumulados.
12. El Master junta todos los `TaskResult`.
13. `RouteMonthCsvWriter` fusiona los acumulados y genera la matriz CSV final.

## Particionamiento del archivo

El archivo se divide por rangos de bytes. La configuracion actual esta en `MasterNode/src/main/resources/config.master`:

```properties
Master.ChunkSizeBytes=134217728
Master.ChunkOverlapBytes=1048576
Master.RemoteReadSizeBytes=8388608
```

Esto significa:

- Cada chunk logico es de 128 MB.
- Cada chunk, excepto el primero, lee 1 MB adicional hacia atras.
- Cada Worker pide bloques remotos de 8 MB al Master mientras procesa su chunk.

El solapamiento es importante porque la velocidad de un bus se calcula con dos datagramas consecutivos. Si un par queda partido entre chunks, el Worker necesita contexto anterior para no perder la medicion.

Para cada chunk se manejan tres offsets:

- `readStart`: desde donde el Worker empieza a leer bytes.
- `endOffset`: donde termina el rango leido.
- `calculationStartOffset`: desde donde el Worker puede empezar a contar resultados.

Los datagramas leidos antes de `calculationStartOffset` sirven como contexto, pero no se contabilizan como resultados propios del chunk. Esto evita duplicar velocidades entre chunks vecinos.

## Lectura remota del archivo

El archivo pesado de datagramas permanece en la maquina del Master. Los Workers no necesitan una copia local.

`FileProviderI` abre el archivo con `FileChannel` y responde llamadas remotas:

```java
readChunk(long offset, int size)
```

`DatagramReader`, en el Worker, usa `RemoteRangeSource` para pedir bloques al Master. Si el rango empieza en medio de una linea CSV, el lector salta la linea parcial inicial y empieza en la siguiente linea completa. Tambien elimina `\r`, reconoce `\n` como fin de linea y asigna a cada datagrama su `sourceOffset`.

## Procesamiento dentro de cada Worker

`ChunkProcessor` crea dos hilos internos por chunk:

- `DatagramReader`: produce datagramas validos desde el rango remoto.
- `SpeedCalculator`: consume datagramas desde una `BlockingQueue`.

La cola es una `ArrayBlockingQueue` de 20000 elementos. El lector envia un `POISON_PILL` al terminar para que el calculador cierre correctamente.

Este flujo permite separar lectura/parsing del calculo de velocidad dentro de cada Worker sin cargar el chunk completo como objetos en memoria.

## Reglas de calculo de velocidad

`SpeedCalculator` aplica las reglas de negocio usadas para aceptar o descartar mediciones:

- Mantiene la ultima posicion vista por bus.
- Solo calcula velocidad cuando existe un datagrama anterior del mismo bus.
- Solo calcula si ambos datagramas tienen el mismo `tripId` y el mismo `lineId`.
- Ignora datagramas con coordenadas invalidas.
- Rechaza diferencias de tiempo menores o iguales a 0 segundos.
- Rechaza diferencias de tiempo mayores a 300 segundos.
- Usa distancia GPS con Haversine.
- Rechaza velocidades menores o iguales a 0 km/h.
- Rechaza velocidades mayores a 120 km/h.
- Agrupa resultados por `lineId` y mes `yyyy-MM`.

El odometro no se usa en el calculo. El codigo documenta que el calculo oficial usa distancia GPS y diferencia temporal porque el odometro no es confiable en buses antiguos del MIO.

## Agregacion de resultados

Cada Worker devuelve resultados parciales como:

```text
lineId, month, speedSum, count
```

El Master no promedia promedios. Primero suma todos los `speedSum` y todos los `count` de la misma ruta-mes, y despues calcula:

```text
averageSpeed = totalSpeedSum / totalCount
```

Esto ocurre en `RouteMonthCsvWriter.aggregate(...)`.

El CSV final usa el catalogo `lines-241-ActiveGT.csv` para generar una fila por cada ruta activa. Las columnas son los meses encontrados en los datos.

## Balanceo y tolerancia a fallos

`MasterController.executeWorkStealing(...)` usa una `ConcurrentLinkedQueue` de chunks. Cada hilo asociado a un Worker toma el siguiente chunk disponible cuando termina el anterior.

Esta estrategia funciona como balanceo dinamico:

- Si un Worker termina rapido, toma mas chunks.
- Si un Worker es mas lento, no bloquea el avance de los demas.
- Si hay maquinas con capacidades diferentes, la carga se reparte segun el ritmo real de cada una.

Si un chunk falla, el Master lo reintenta. En el codigo actual:

```java
MAX_CHUNK_ATTEMPTS = 3
```

Si despues de esos intentos el chunk sigue fallando, el analisis se aborta para evitar generar un CSV incompleto.

## Patrones usados en la version distribuida

Esta version usa varios patrones y estilos de arquitectura que se pueden ver directamente en el codigo. Se listan porque ayudan a justificar como la solucion cumple el objetivo distribuido del proyecto.

Las fuentes externas se usan como apoyo conceptual. La justificacion principal sale del codigo de esta version distribuida.

### Master-Worker

**Por que se uso**

Permite dividir un archivo grande en tareas independientes y repartirlas entre varios nodos. El Master conserva la responsabilidad de coordinacion, mientras que los Workers se concentran en procesar chunks. Esto ayuda a escalar horizontalmente: agregar mas workers permite procesar mas rangos en paralelo.

Como apoyo, el patron Master-Worker descrito por GigaSpaces y el modelo MapReduce de Dean y Ghemawat respaldan esta idea de partir un trabajo grande, procesarlo en paralelo y combinar resultados parciales.

**Donde se ve reflejado**

- `MasterNode/src/main/java/Demo/MasterNode.java`: inicia el nodo Master, publica el servicio de archivo y lanza el analisis.
- `MasterNode/src/main/java/org/example/scheduler/JobScheduler.java`: divide el archivo en chunks y crea la cola de trabajo.
- `MasterNode/src/main/java/org/example/controller/MasterController.java`: conecta con los workers y les asigna chunks.
- `WorkerNode/src/main/java/Demo/WorkerNode.java`: inicia cada nodo Worker.
- `WorkerNode/src/main/java/Demo/WorkerI.java`: recibe la tarea remota enviada por el Master.

Referencias de apoyo:

- GigaSpaces, Master-Worker Pattern: https://docs.gigaspaces.com/ie-resources/solution-hub/master-worker-pattern.html
- Dean y Ghemawat, MapReduce: https://research.google.com/archive/mapreduce-osdi04.pdf

### Proxy remoto / RPC con ZeroC Ice

**Por que se uso**

Permite que el Master invoque metodos en Workers remotos como si fueran objetos locales, y que los Workers pidan bloques del archivo al Master sin compartir disco ni copiar el dataset completo. Esto separa la ubicacion fisica de los nodos de la logica de procesamiento.

ZeroC Ice esta pensado para construir aplicaciones distribuidas con RPC, y el patron Proxy ayuda a representar un objeto remoto mediante una referencia local. En el proyecto, eso se ve en los proxies hacia los Workers y hacia el `FileProvider` del Master.

**Donde se ve reflejado**

- `MasterNode/src/main/slice/Compute.ice` y `WorkerNode/src/main/slice/Compute.ice`: definen las interfaces remotas `Worker` y `FileProvider`.
- `MasterController.toWorkerProxy(...)`: construye proxies Ice hacia `SimpleWorker`.
- `MasterController.addWorker(...)`: valida los proxies con `checkedCast` e `ice_ping`.
- `Demo.FileProviderI`: implementa `FileProvider.readChunk(...)` en el Master.
- `DatagramReader.RemoteRangeSource`: usa `fileProvider.readChunk(offset, size)` desde el Worker.

Referencias de apoyo:

- ZeroC Ice: https://www.zeroc.com/
- ZeroC Ice Java API, ObjectAdapter: https://doc.zeroc.com/api/ice/3.7/java/com/zeroc/Ice/ObjectAdapter.html
- Refactoring Guru, Proxy: https://refactoring.guru/design-patterns/proxy

### Work-Stealing / cola compartida de tareas

**Por que se uso**

Ayuda a balancear carga cuando los workers no tienen el mismo rendimiento o cuando algunos chunks tardan mas que otros. En vez de asignar una cantidad fija de chunks a cada Worker desde el inicio, todos toman trabajo desde una cola comun. El Worker que termina primero toma otro chunk.

La idea se relaciona con estrategias de work-stealing usadas para mejorar el balanceo dinamico: los procesadores que quedan libres toman trabajo pendiente. En este proyecto se implementa de forma simple con una cola compartida de chunks.

**Donde se ve reflejado**

- `MasterController.executeWorkStealing(...)`: crea una `ConcurrentLinkedQueue<ChunkTask>`.
- `WorkerThread.run()`: cada hilo hace `chunkQueue.poll()` para tomar el siguiente chunk disponible.
- Los contadores `completedChunks`, `terminalChunks` y `failedChunks` permiten reportar progreso y controlar terminacion.

Referencias de apoyo:

- Blumofe et al., Cilk: https://dl.acm.org/doi/10.1145/209937.209958
- Copia MIT CSAIL de Cilk: https://people.csail.mit.edu/matei/courses/2015/6.S897/readings/cilk.pdf

### Facade

**Por que se uso**

Da una entrada simple para iniciar el analisis sin exponer al punto de entrada todos los detalles del scheduler. Esto mantiene `MasterNode` mas legible y concentra la coordinacion interna en las clases correspondientes.

Esto coincide con la intencion del patron Facade: ofrecer una interfaz mas simple sobre un conjunto de clases internas.

**Donde se ve reflejado**

- `MasterNode/src/main/java/org/example/facade/SystemFacade.java`: expone `startAnalysis(String filePath)`.
- `Demo.MasterNode`: usa `new SystemFacade(scheduler).startAnalysis(selectedFile)` para iniciar el procesamiento.

Referencia de apoyo:

- Refactoring Guru, Facade: https://refactoring.guru/design-patterns/facade

### Producer-Consumer

**Por que se uso**

Separa la lectura/parsing de datagramas del calculo de velocidad dentro de cada Worker. El lector produce datagramas y el calculador los consume. Esto evita cargar todo el chunk como objetos en memoria y permite que lectura y calculo avancen de forma desacoplada con backpressure.

El soporte tecnico directo en Java es `BlockingQueue`, que permite coordinar un productor y un consumidor de forma segura entre hilos.

**Donde se ve reflejado**

- `WorkerNode/src/main/java/org/example/core/ChunkProcessor.java`: crea una `ArrayBlockingQueue<Datagram>` y dos hilos.
- `DatagramReader`: produce datagramas y los inserta en la cola.
- `SpeedCalculator`: consume datagramas desde la cola.
- `DatagramReader.POISON_PILL`: marca el final de la produccion para cerrar el consumidor.

Referencia de apoyo:

- Oracle Java API, BlockingQueue: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingQueue.html

### Scheduler / Job Scheduler

**Por que se uso**

Centraliza la planificacion del trabajo distribuido: determina el tamano del archivo, calcula rangos, aplica solapamiento, llama al controlador y finalmente genera el reporte. Esto evita mezclar la configuracion del trabajo con la implementacion remota de Ice.

**Donde se ve reflejado**

- `MasterNode/src/main/java/org/example/scheduler/IJobManager.java`: define la operacion de planificacion.
- `MasterNode/src/main/java/org/example/scheduler/JobScheduler.java`: implementa `scheduleJob(...)`, crea chunks, despacha el trabajo y llama al escritor CSV.

### Adapter

**Por que se uso**

Permite adaptar el contrato remoto Ice a clases internas de procesamiento. El Worker recibe una llamada con tipos generados por Slice, pero internamente usa `ProcessingEngine`, `ChunkProcessor`, `ChunkResult` y `RouteMonthResult`.

Esto coincide con la idea del patron Adapter: conectar una interfaz externa con una implementacion interna que usa otros tipos o una estructura diferente.

**Donde se ve reflejado**

- `WorkerNode/src/main/java/Demo/WorkerI.java`: adapta la llamada remota `processDatagramLog(...)` hacia `ProcessingEngine`.
- `WorkerNode/src/main/java/org/example/engine/ProcessingEngine.java`: convierte `ChunkResult` a `Demo.TaskResult` y `RouteMonthResult` a `Demo.RouteMonthSpeed`.

Referencia de apoyo:

- Refactoring Guru, Adapter: https://refactoring.guru/design-patterns/adapter

### Result Aggregator / Reduce

**Por que se uso**

Cada Worker calcula resultados parciales. El Master necesita fusionarlos correctamente para obtener una sola matriz final. La agregacion evita promediar promedios: suma primero `speedSum` y `count`, y solo al final calcula la velocidad promedio.

Este rol es similar al Aggregator de Enterprise Integration Patterns y tambien a la fase Reduce de MapReduce: recibir resultados parciales y consolidarlos en una salida final.

**Donde se ve reflejado**

- `MasterNode/src/main/java/org/example/report/RouteMonthCsvWriter.java`: `aggregate(...)` combina todos los `TaskResult`.
- `RouteMonthCsvWriter.Totals`: acumula `speedSum` y `count`.
- `writeMatrix(...)`: calcula `speedSum / count` para cada celda ruta-mes.

Referencias de apoyo:

- Enterprise Integration Patterns, Aggregator: https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html
- Dean y Ghemawat, MapReduce: https://research.google.com/archive/mapreduce-osdi04.pdf

### Retry / tolerancia basica a fallos

**Por que se uso**

En una ejecucion distribuida pueden fallar llamadas remotas, workers o lecturas de red. Reintentar chunks evita abortar inmediatamente por fallos transitorios. Si el chunk falla definitivamente, el sistema aborta para no entregar un CSV incompleto.

Esta decision se alinea con el patron Retry: reintentar operaciones que pueden fallar temporalmente, pero con un limite para no ocultar fallos permanentes.

**Donde se ve reflejado**

- `MasterController.MAX_CHUNK_ATTEMPTS = 3`.
- `WorkerThread.run()`: incrementa `chunk.attempts`, reencola el chunk si aun quedan intentos y registra fallos definitivos.
- `executeWorkStealing(...)`: lanza una excepcion si hubo chunks fallidos despues de los reintentos.

Referencia de apoyo:

- Microsoft Azure Architecture Center, Retry pattern: https://learn.microsoft.com/en-us/azure/architecture/patterns/retry

Referencia general:

- Enterprise Integration Patterns: https://www.enterpriseintegrationpatterns.com/

## Configuracion del Master

Archivo:

```text
MasterNode/src/main/resources/config.master
```

Campos principales:

```properties
Ice.Default.Host=192.168.131.103
Ice.MessageSizeMax=65536

FileProviderAdapter.Endpoints=tcp -h 192.168.131.103 -p 11000 -t 600000

Master.WorkerHosts=192.168.131.104:10000,192.168.131.105:10000
Master.WorkerTimeoutMs=600000

Master.ChunkSizeBytes=134217728
Master.ChunkOverlapBytes=1048576
Master.RemoteReadSizeBytes=8388608
Master.Verbose=false

Master.ActiveLinesFile=lines-241-ActiveGT.csv
Master.OutputCsv=route_month_speeds.csv
```

Notas:

- `Ice.Default.Host` y `FileProviderAdapter.Endpoints` deben usar la IP real del Master cuando se ejecuta en varias maquinas.
- `Master.WorkerHosts` contiene los workers disponibles, separados por coma o punto y coma.
- `Ice.MessageSizeMax` debe ser mayor que `Master.RemoteReadSizeBytes`.
- `Master.Verbose=false` reduce logs para que la consola no afecte las mediciones.

## Configuracion del Worker

Archivo:

```text
WorkerNode/src/main/resources/config.worker
```

Campos principales:

```properties
Ice.MessageSizeMax=65536
WorkerAdapter.Endpoints=tcp -p 10000
Master.RemoteReadSizeBytes=8388608
Master.Verbose=false
```

Si cada Worker corre en una maquina distinta, pueden usar el mismo puerto `10000`. Si varios Workers corren en la misma maquina, se deben usar archivos de configuracion con puertos distintos, por ejemplo:

- `config.worker`
- `config.worker.10001`
- `config.worker.10002`

En ese caso, `Master.WorkerHosts` debe apuntar a cada puerto:

```properties
Master.WorkerHosts=localhost:10000,localhost:10001,localhost:10002
```

## Compilacion

Desde `SITM-MIO/MasterNode`:

```bash
./gradlew shadowJar
```

Desde `SITM-MIO/WorkerNode`:

```bash
./gradlew shadowJar
```

En Windows PowerShell:

```powershell
.\gradlew.bat shadowJar
```

JARs generados:

```text
MasterNode/build/libs/master-node.jar
WorkerNode/build/libs/worker-node.jar
```

## Ejecucion

Primero se levantan los Workers. Luego se inicia el Master.

Worker:

```bash
cd WorkerNode
java -jar build/libs/worker-node.jar --Ice.Config=src/main/resources/config.worker
```

Master:

```bash
cd MasterNode
java -jar build/libs/master-node.jar --Ice.Config=src/main/resources/config.master
```

Al iniciar, el Master muestra un menu para escoger el archivo:

- `../data/datagrams-MiniPilot.csv`: prueba local rapida.
- `/home/swarch/Documents/data/datagrams4Pilot.csv`: archivo pilot final usado en el entorno de laboratorio.
- Ruta manual ingresada por consola.

El archivo debe existir en la maquina del Master. Los Workers lo leen remotamente por Ice.

## Salida

El Master genera:

```text
route_month_speeds.csv
```

Formato general:

```text
route_id,route_short_name,route_description,2018-05,2018-06,...
131,"T31","Descripcion de la ruta",25.31,27.44,...
```

Caracteristicas:

- Una fila por ruta activa.
- Una columna por mes encontrado en las mediciones.
- Celdas vacias cuando una ruta no tiene datos para un mes.
- Promedio de velocidad en km/h.
- Valores calculados con suma ponderada, no con promedio de promedios.

## Metricas impresas

Durante la ejecucion, el Master reporta:

- Workers conectados.
- Archivo seleccionado y tamano.
- Tamano de chunk.
- Solapamiento.
- Tamano de lectura remota.
- Total de chunks planificados.
- Progreso de chunks completados.
- Worker que termino el ultimo chunk reportado.
- Tiempo remoto de llamada y tiempo interno del Worker.

Al final, `JobScheduler.printSummary(...)` imprime:

```text
SITM-MIO - REPORTE DISTRIBUIDO FINAL
[Scheduler] Tiempo total cluster
[Scheduler] Total datagramas
[Scheduler] Total velocidades
[Scheduler] Celdas ruta-mes
[Scheduler] CSV final guardado en
```

Estas metricas permiten comparar la version distribuida contra la monolitica y la version `ThreadPool`.


La comparacion debe hacerse con el mismo archivo de entrada y conservando los parametros relevantes (`ChunkSizeBytes`, `ChunkOverlapBytes`, `RemoteReadSizeBytes`) para que los resultados sean comparables.

## Consideraciones importantes

- El Master debe ser alcanzable por todos los Workers en el endpoint de `FileProviderAdapter`.
- Cada Worker debe ser alcanzable por el Master en el puerto configurado.
- `Master.RemoteReadSizeBytes` debe ser menor que `Ice.MessageSizeMax`.
- Si se cambia el tamano de chunk, tambien debe evaluarse si el solapamiento sigue siendo suficiente.
- Si falla un chunk definitivamente, el sistema aborta el analisis y no genera un CSV parcial como resultado valido.
- El catalogo de rutas activas usado para la salida esta en `MasterNode/src/main/resources/lines-241-ActiveGT.csv`.
- Para pruebas grandes, conviene mantener `Master.Verbose=false` para no distorsionar los tiempos por exceso de impresion en consola.
