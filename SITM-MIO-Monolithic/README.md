# SITM-MIO — Versión Monolítica

## Objetivo del proyecto

Calcula la **velocidad promedio por ruta por mes** para todas las rutas activas del SITM-MIO
(Sistema Integrado de Transporte Masivo de Occidente, Cali) a partir de datagramas GPS registrados
por los buses. Procesa el archivo de datagramas de forma **secuencial en un solo hilo**, sin
concurrencia ni distribución.

Esta versión es la **Versión 1** del proyecto final de Ingeniería de Software IV (Universidad Icesi),
y sirve como línea base para comparar tiempos de ejecución con las versiones ThreadPool y
distribuida.

---

## Relación con el enunciado del proyecto

El enunciado solicita:
1. ✅ **Versión monolítica** — esta versión.
2. Versión con ThreadPool — ver `SITM-MIO-ThreadPool`.
3. Versión distribuida con ZeroC Ice — ver `SITM-MIO repo`.

---

## Ubicación del archivo de datos

Coloca el archivo de datagramas en:

```
SITM-MIO-Monolithic/
└── data/
    └── datagrams-MiniPilot.csv   ← aquí
```

El archivo `lines-241-ActiveGT.csv` ya está incluido en `src/main/resources/` y se copia
automáticamente al classpath durante el build.

---

## Cómo compilar

```bash
cd SITM-MIO-Monolithic
./gradlew shadowJar
```

Esto genera: `build/libs/sitm-mio-monolithic.jar`

---

## Cómo ejecutar

### Con ruta por defecto (`data/datagrams-MiniPilot.csv`):

```bash
java -jar build/libs/sitm-mio-monolithic.jar
```

### Con ruta personalizada:

```bash
java -jar build/libs/sitm-mio-monolithic.jar /ruta/a/datagrams-MiniPilot.csv
```

### Con ruta de datagramas y ruta de rutas activas:

```bash
java -jar build/libs/sitm-mio-monolithic.jar data/datagrams-MiniPilot.csv lines-241-ActiveGT.csv
```

---

## Formato del CSV de salida

Archivo: `route_month_speeds_monolithic.csv`

```
route_id,route_short_name,route_description,2018-06,2018-07,...
101,"T31","Terminal - Universidades - Guadalupe",32.4512,28.1034,...
...
```

- Una fila por cada ruta activa en `lines-241-ActiveGT.csv`.
- Una columna por cada mes con datos en el dataset.
- Celdas vacías si la ruta no tiene mediciones ese mes.
- Velocidades en **km/h** con 4 decimales.

---

## Métricas impresas en consola

Al finalizar, el programa imprime:

```
[Monolithic] Archivo de entrada:        data/datagrams-MiniPilot.csv
[Monolithic] Tamaño del archivo:        XXX bytes (XX.XX MB)
[Monolithic] Datagramas procesados:     XXX,XXX
[Monolithic] Mediciones de velocidad:   XXX,XXX
[Monolithic] Celdas ruta-mes con datos: XXX
[Monolithic] Tiempo total:              XXX ms (XX.XX s)
[Monolithic] Throughput:                XX.XX MB/s
[Monolithic] CSV generado:              route_month_speeds_monolithic.csv
```

---

## Reglas de negocio aplicadas

- Solo se comparan datagramas **consecutivos del mismo bus**.
- Solo se calcula velocidad si el datagrama previo tiene el **mismo `lineId`**.
- Se ignoran datagramas con coordenadas inválidas.
- Se ignoran pares con delta de tiempo `<= 0` o `>= 300` segundos.
- Se ignoran velocidades `<= 0` o `>= 120 km/h`.
- El promedio se calcula como `speedSum / count` (sin promediar promedios).
- La distancia se calcula con la fórmula de **Haversine**.

---

## Limitaciones / Supuestos

- El archivo de datagramas debe estar ordenado cronológicamente por bus para mejores resultados
  (el algoritmo mantiene solo el último datagrama visto por bus).
- Procesamiento de un solo hilo: adecuado para `datagrams-MiniPilot.csv`. Para archivos más
  grandes (`datagrams4Pilot.csv`), considera la versión ThreadPool.
- Se requiere Java 17 o superior.
