# SITM-MIO - Version monolitica

Esta carpeta contiene la version monolitica del proyecto final de Ingenieria de Software IV para el analisis de datagramas del SITM-MIO.

El objetivo de esta version es resolver el problema de forma directa y secuencial: leer el archivo de datagramas, calcular velocidades entre posiciones consecutivas de un mismo bus y generar una matriz con la velocidad promedio de cada ruta activa por mes. Sirve como linea base para comparar la mejora de rendimiento de la version con `ThreadPool` y de la version distribuida con ZeroC Ice.

## Relacion con el enunciado

El enunciado pide construir varias formas de procesar el mismo problema. Esta implementacion corresponde a la primera version:

- Version monolitica: procesa todo el archivo en un solo proceso y en un solo hilo.
- Version concurrente local: esta en `SITM-MIO-ThreadPool`.
- Version distribuida: esta en `SITM-MIO`, separada en `MasterNode` y `WorkerNode`.

En esta version no hay particionamiento del archivo, no hay hilos de trabajo y no hay comunicacion remota. Todo el calculo ocurre dentro de la misma aplicacion Java.

## Que hace

La aplicacion toma un CSV de datagramas GPS de buses del MIO y calcula, para cada ruta y mes, la velocidad promedio observada.

El flujo general es:

1. Lee el archivo `data/datagrams-MiniPilot.csv`, o el archivo indicado por argumento.
2. Convierte cada linea valida en un datagrama.
3. Mantiene el ultimo datagrama visto por cada bus.
4. Cuando llega un nuevo datagrama del mismo bus y de la misma ruta, calcula la distancia entre ambos puntos y la convierte a velocidad.
5. Agrupa las velocidades por `lineId` y mes.
6. Carga el catalogo de rutas activas `lines-241-ActiveGT.csv`.
7. Genera el archivo `route_month_speeds_monolithic.csv`.

El resultado final es una matriz donde cada fila es una ruta activa y cada columna mensual contiene la velocidad promedio en km/h.

## Reglas de calculo

Para evitar mediciones inconsistentes, el programa aplica estas reglas:

- Solo compara datagramas consecutivos del mismo bus.
- Solo calcula velocidad si ambos datagramas pertenecen al mismo `lineId`.
- Ignora coordenadas invalidas.
- Ignora diferencias de tiempo menores o iguales a 0 segundos.
- Ignora diferencias de tiempo mayores o iguales a 300 segundos.
- Ignora velocidades menores o iguales a 0 km/h.
- Ignora velocidades mayores o iguales a 120 km/h.
- Calcula el promedio como `speedSum / count`, no como promedio de promedios.
- Usa distancia Haversine entre coordenadas.

## Archivos importantes

- `src/main/java/org/example/monolithic/MonolithicApp.java`: punto de entrada de la aplicacion.
- `src/main/java/org/example/io/DatagramFileReader.java`: lector secuencial del archivo de datagramas.
- `src/main/java/org/example/core/RouteMonthAccumulator.java`: acumula velocidades por ruta y mes.
- `src/main/java/org/example/report/RouteMonthCsvWriter.java`: escribe la matriz final en CSV.
- `src/main/resources/lines-241-ActiveGT.csv`: catalogo de rutas activas.
- `data/`: carpeta esperada para ubicar el CSV de datagramas de prueba.

## Datos de entrada

Por defecto, la aplicacion espera el archivo:

```text
SITM-MIO-Monolithic/data/datagrams-MiniPilot.csv
```

Tambien se puede pasar otra ruta como argumento al ejecutar el JAR.

El catalogo de rutas activas ya esta incluido en:

```text
src/main/resources/lines-241-ActiveGT.csv
```

## Compilacion

En Windows PowerShell:

```powershell
cd SITM-MIO-Monolithic
.\gradlew.bat shadowJar
```

En macOS, Linux o Git Bash:

```bash
cd SITM-MIO-Monolithic
./gradlew shadowJar
```

El JAR ejecutable queda en:

```text
build/libs/sitm-mio-monolithic.jar
```

## Ejecucion

Con la ruta por defecto:

```bash
java -jar build/libs/sitm-mio-monolithic.jar
```

Con una ruta personalizada para los datagramas:

```bash
java -jar build/libs/sitm-mio-monolithic.jar data/datagrams-MiniPilot.csv
```

Con ruta personalizada para datagramas y rutas activas:

```bash
java -jar build/libs/sitm-mio-monolithic.jar data/datagrams-MiniPilot.csv lines-241-ActiveGT.csv
```

## Salida

La aplicacion genera:

```text
route_month_speeds_monolithic.csv
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
- Lineas validas y lineas omitidas.
- Datagramas procesados.
- Mediciones de velocidad aceptadas.
- Celdas ruta-mes con datos.
- Tiempo total de ejecucion.
- Throughput en MB/s.
- Ruta absoluta del CSV generado.

Estas metricas son la referencia para comparar contra la version `ThreadPool` y contra la version distribuida.

## Supuestos y limitaciones

- El procesamiento es de un solo hilo, por lo que su rendimiento depende de una sola ejecucion secuencial.
- El algoritmo guarda solo el ultimo datagrama visto por bus; por eso conviene que el archivo mantenga un orden temporal razonable.
- Esta version es adecuada como punto de comparacion y prueba funcional. Para aprovechar varios nucleos en la misma maquina, usar `SITM-MIO-ThreadPool`.
- Requiere Java 17 o superior.
