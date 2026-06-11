# SITM-MIO - Version ThreadPool

Esta carpeta contiene la version concurrente local del proyecto final de Ingenieria de Software IV para el analisis de datagramas del SITM-MIO.

El objetivo de esta version es resolver el mismo calculo de la version monolitica, pero usando varios hilos dentro de una sola maquina. Para lograrlo, el archivo de datagramas se divide en bloques de bytes y cada bloque se procesa en paralelo con un `ExecutorService`.

## Relacion con el enunciado

El enunciado pide comparar diferentes formas de procesar el mismo problema. Esta implementacion corresponde a la version con `ThreadPool`:

- Version monolitica: esta en `SITM-MIO-Monolithic`.
- Version concurrente local: esta carpeta.
- Version distribuida: esta en `SITM-MIO`, separada en `MasterNode` y `WorkerNode`.

Esta version ayuda a medir cuanto mejora el procesamiento cuando se aprovechan varios nucleos de una misma maquina, antes de pasar al escenario distribuido con varios nodos.

## Que hace

La aplicacion toma un CSV de datagramas GPS de buses del MIO y genera una matriz de velocidad promedio por ruta activa y por mes.

El flujo general es:

1. Lee el tamano del archivo de datagramas.
2. Divide el archivo en chunks locales de 10 MB.
3. Agrega un solapamiento de 1 MB hacia atras en cada chunk, excepto en el primero.
4. Crea una tarea `ChunkProcessor` por cada chunk.
5. Ejecuta las tareas con un pool fijo de hilos.
6. Cada tarea calcula acumulados parciales de velocidad por ruta y mes.
7. La aplicacion principal fusiona los resultados sumando `speedSum` y `count`.
8. Carga el catalogo de rutas activas `lines-241-ActiveGT.csv`.
9. Genera el archivo `route_month_speeds_threadpool.csv`.

El calculo funcional es el mismo que en la version monolitica. La diferencia esta en la estrategia de ejecucion: aqui el archivo se procesa por partes y en paralelo.

## Por que hay solapamiento

Para calcular la velocidad de un bus se necesitan dos datagramas consecutivos. Si el archivo se corta exactamente en chunks, una medicion podria quedar partida entre dos bloques.

Por eso cada chunk, desde el segundo en adelante, lee 1 MB adicional antes de su inicio logico:

- `readStart`: desde donde el chunk empieza a leer bytes.
- `calculationStart`: desde donde el chunk puede empezar a contar resultados.
- `readEnd`: fin del rango asignado.

Los datagramas del solapamiento sirven como contexto para conocer la posicion previa del bus, pero no se cuentan como resultados propios del chunk. Asi se reduce el riesgo de perder mediciones en los bordes y se evita contar dos veces la misma velocidad.

## Reglas de calculo

Las reglas son equivalentes a las de la version monolitica:

- Solo compara datagramas consecutivos del mismo bus dentro del contexto disponible.
- Solo calcula velocidad si ambos datagramas pertenecen al mismo `lineId`.
- Ignora coordenadas invalidas.
- Ignora diferencias de tiempo menores o iguales a 0 segundos.
- Ignora diferencias de tiempo mayores o iguales a 300 segundos.
- Ignora velocidades menores o iguales a 0 km/h.
- Ignora velocidades mayores o iguales a 120 km/h.
- Agrupa por ruta y mes.
- Fusiona resultados con suma ponderada: primero suma `speedSum` y `count`, luego calcula `speedSum / count`.

En esta version se usa una aproximacion rapida de distancia adaptada al caso local para reducir costo de CPU durante el procesamiento por chunks.

## Archivos importantes

- `src/main/java/org/example/concurrent/ThreadPoolApp.java`: punto de entrada y coordinador del pool.
- `src/main/java/org/example/scheduler/LocalChunkPlanner.java`: divide el archivo en chunks de 10 MB con 1 MB de solapamiento.
- `src/main/java/org/example/core/ChunkProcessor.java`: procesa un chunk y retorna sus resultados parciales.
- `src/main/java/org/example/core/RouteMonthAccumulator.java`: acumula velocidades por ruta y mes dentro de cada chunk.
- `src/main/java/org/example/report/RouteMonthCsvWriter.java`: escribe la matriz final en CSV.
- `src/main/resources/lines-241-ActiveGT.csv`: catalogo de rutas activas.
- `data/`: carpeta esperada para ubicar el CSV de datagramas de prueba.

## Datos de entrada

Por defecto, la aplicacion espera el archivo:

```text
SITM-MIO-ThreadPool/data/datagrams-MiniPilot.csv
```

Tambien se puede pasar otra ruta como argumento al ejecutar el JAR.

El catalogo de rutas activas ya esta incluido en:

```text
src/main/resources/lines-241-ActiveGT.csv
```

## Compilacion

En Windows PowerShell:

```powershell
cd SITM-MIO-ThreadPool
.\gradlew.bat shadowJar
```

En macOS, Linux o Git Bash:

```bash
cd SITM-MIO-ThreadPool
./gradlew shadowJar
```

El JAR ejecutable queda en:

```text
build/libs/sitm-mio-threadpool.jar
```

## Ejecucion

Con ruta por defecto y numero de hilos automatico:

```bash
java -jar build/libs/sitm-mio-threadpool.jar
```

Con ruta personalizada:

```bash
java -jar build/libs/sitm-mio-threadpool.jar data/datagrams-MiniPilot.csv
```

Con ruta personalizada y numero de hilos:

```bash
java -jar build/libs/sitm-mio-threadpool.jar data/datagrams-MiniPilot.csv 4
```

Con ruta de datagramas, numero de hilos y rutas activas:

```bash
java -jar build/libs/sitm-mio-threadpool.jar data/datagrams-MiniPilot.csv 8 lines-241-ActiveGT.csv
```

Si no se indica el numero de hilos, se usa:

```text
Runtime.getRuntime().availableProcessors()
```

## Salida

La aplicacion genera:

```text
route_month_speeds_threadpool.csv
```

Formato general:

```text
route_id,route_short_name,route_description,2018-06,2018-07,...
101,"T31","Descripcion de la ruta",32.4512,28.1034,...
```

Cada fila corresponde a una ruta activa. Las columnas mensuales se crean con los meses encontrados en los resultados. Si una ruta no tiene mediciones para un mes, la celda queda vacia.

## Metricas en consola

Al finalizar, el programa imprime un resumen con:

- Archivo procesado.
- Tamano del archivo.
- Tamano de chunk.
- Solapamiento usado.
- Cantidad total de chunks.
- Numero de hilos del pool.
- Datagramas procesados.
- Mediciones de velocidad aceptadas.
- Celdas ruta-mes con datos.
- Tiempo total de pared.
- Tiempo acumulado de chunks.
- Speedup operativo.
- Throughput en MB/s.
- Chunk mas rapido y chunk mas lento.
- Detalle de cada chunk.
- Ruta absoluta del CSV generado.

Estas metricas permiten comparar la ejecucion contra la version monolitica y observar si aumentar los hilos mejora el tiempo total.

## Manejo de errores

Si algun chunk falla o no se completa la cantidad esperada de chunks, la aplicacion reporta el error y no genera el CSV. Esto evita entregar un archivo parcial como si fuera un resultado valido.

## Supuestos y limitaciones

- El procesamiento sigue ocurriendo en una sola maquina; no hay comunicacion remota.
- El solapamiento de 1 MB busca conservar contexto entre chunks, pero archivos con separaciones muy grandes entre datagramas de un mismo bus podrian requerir otro valor.
- Como varios hilos leen y procesan partes del mismo archivo, el rendimiento depende del numero de nucleos, del disco y del tamano del dataset.
- Para datasets grandes puede ser necesario ejecutar Java con mas memoria, por ejemplo `-Xmx4g`.
- Requiere Java 17 o superior.
