# SITM-MIO — Versión Concurrente ThreadPool

## Objetivo del proyecto

Calcula la **velocidad promedio por ruta por mes** para todas las rutas activas del SITM-MIO
(Sistema Integrado de Transporte Masivo de Occidente, Cali) usando un **pool de hilos local**
(`ExecutorService`). El archivo de datagramas se divide en **chunks de 10 MB** con solapamiento
de 1 MB para evitar perder mediciones en los límites de cada chunk.

Esta versión es la **Versión 2** del proyecto final de Ingeniería de Software IV (Universidad Icesi).
Permite medir el *speedup* respecto a la versión monolítica y determinar a partir de cuántos
núcleos vale la pena distribuir la solución.

---

## Relación con el enunciado del proyecto

El enunciado solicita:
1. Versión monolítica — ver `SITM-MIO-Monolithic`.
2. ✅ **Versión con ThreadPool** — esta versión.
3. Versión distribuida con ZeroC Ice — ver `SITM-MIO repo`.

---

## Ubicación del archivo de datos

Coloca el archivo de datagramas en:

```
SITM-MIO-ThreadPool/
└── data/
    └── datagrams-MiniPilot.csv   ← aquí
```

El archivo `lines-241-ActiveGT.csv` ya está incluido en `src/main/resources/` y se copia
automáticamente al classpath durante el build.

---

## Cómo compilar

En Windows PowerShell:

```powershell
cd SITM-MIO-ThreadPool
.\gradlew.bat shadowJar
```

En macOS/Linux/Git Bash:

```bash
cd SITM-MIO-ThreadPool
./gradlew shadowJar
```

Esto genera: `build/libs/sitm-mio-threadpool.jar`

---

## Cómo ejecutar

### Con ruta por defecto y número de hilos automático:

```bash
java -jar build/libs/sitm-mio-threadpool.jar
```

### Con ruta personalizada:

```bash
java -jar build/libs/sitm-mio-threadpool.jar /ruta/a/datagrams-MiniPilot.csv
```

### Con ruta y número de hilos explícito:

```bash
java -jar build/libs/sitm-mio-threadpool.jar data/datagrams-MiniPilot.csv 4
```

Si no se indica el número de hilos, se usa `Runtime.getRuntime().availableProcessors()`.

### Con rutas personalizadas para todo:

```bash
java -jar build/libs/sitm-mio-threadpool.jar data/datagrams-MiniPilot.csv 8 lines-241-ActiveGT.csv
```

---

## Formato del CSV de salida

Archivo: `route_month_speeds_threadpool.csv`

```
route_id,route_short_name,route_description,2018-06,2018-07,...
101,"T31","Terminal - Universidades - Guadalupe",32.4512,28.1034,...
...
```

- Una fila por cada ruta activa en `lines-241-ActiveGT.csv`.
- Una columna por cada mes con datos en el dataset.
- Celdas vacías si la ruta no tiene mediciones ese mes.
- Velocidades en **km/h** con 4 decimales.
- Resultado idéntico al de la versión monolítica y distribuida.

---

## Métricas impresas en consola

```
[ThreadPool] Archivo de entrada:         data/datagrams-MiniPilot.csv
[ThreadPool] Tamaño del archivo:         XXX bytes (XX.XX MB)
[ThreadPool] Tamaño de chunk:            10.00 MB
[ThreadPool] Solapamiento:               1.00 MB
[ThreadPool] Total de chunks:            XX
[ThreadPool] Hilos del pool:             N
[ThreadPool] Datagramas procesados:      XXX,XXX
[ThreadPool] Mediciones de velocidad:    XXX,XXX
[ThreadPool] Celdas ruta-mes con datos:  XXX
[ThreadPool] Tiempo total:               XXX ms (XX.XX s)
[ThreadPool] Throughput:                 XX.XX MB/s

Detalle por chunk:
  Chunk  1: XXX ms | XXX,XXX datagramas | XXX,XXX velocidades | XXX ruta-mes
  ...
```

---

## Mecanismo de solapamiento (overlap)

Para evitar perder mediciones en los bordes de los chunks:

- `calculationStart = currentOffset`
- `readStart = max(0, calculationStart - 1 MB)`
- `end = min(currentOffset + 10 MB, fileSize)`

Cada chunk **lee** desde `readStart` pero solo **cuenta** velocidades cuyo datagrama actual
tiene `sourceOffset >= calculationStart`. Esto garantiza que cada medición se cuente exactamente
una vez, sin importar en cuántos chunks cae el bus.

---

## Reglas de negocio aplicadas

Idénticas a la versión monolítica y distribuida:

- Solo se comparan datagramas **consecutivos del mismo bus** (dentro del chunk).
- Solo se calcula velocidad si el datagrama previo tiene el **mismo `lineId`**.
- Se ignoran datagramas con coordenadas inválidas.
- Se ignoran pares con delta de tiempo `<= 0` o `>= 300` segundos.
- Se ignoran velocidades `<= 0` o `>= 120 km/h`.
- Promedio como `speedSum / count` (sin promediar promedios).
- Distancia con **Haversine**.

---

## Limitaciones / Supuestos

- Si un chunk falla, el programa reporta el error claramente y **no genera el CSV**, en lugar
  de producir resultados incompletos sin advertencia.
- El solapamiento de 1 MB cubre la distancia entre dos datagramas consecutivos del mismo bus
  en la mayoría de casos. Un solapamiento mayor reduce el riesgo de perder mediciones en
  archivos con baja densidad temporal.
- Se requiere Java 17 o superior.
- Para archivos de varios GB, aumenta la memoria con `-Xmx4g` si es necesario.
