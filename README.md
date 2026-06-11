# SITM-MIO - Proyecto final

Repositorio del proyecto final de Ingenieria de Software IV sobre el procesamiento de datagramas del SITM-MIO para calcular velocidad promedio por ruta y mes.

## Autores

- Juan Sebastian Rodriguez Legarda - A00405229
- Johan Stiven Suarez - A00404253
- Alejandro Vargas Sanchez - A00404840
- Juan José Muñoz Franco - A00405005

## Contenido del repositorio

- `SITM-MIO-Monolithic`: version monolitica. Procesa el archivo de datagramas de forma secuencial en un solo hilo y sirve como linea base de comparacion.
- `SITM-MIO-ThreadPool`: version concurrente local. Divide el archivo en chunks y los procesa con un pool de hilos en una sola maquina.
- `SITM-MIO`: version distribuida. Usa ZeroC Ice con un esquema Master-Worker; el Master coordina, expone la lectura remota del archivo y genera el CSV final, mientras los Workers procesan chunks.

Cada carpeta contiene su propio README con instrucciones de compilacion, ejecucion, reglas de calculo y detalles relevantes de implementacion.

## Requisitos generales

- Java JDK compatible con cada modulo.
- Gradle Wrapper incluido en los proyectos.
- ZeroC Ice para la version distribuida, gestionado por las dependencias de Gradle.

## Salidas principales

Las versiones generan archivos CSV con una matriz ruta-mes, donde cada fila corresponde a una ruta activa del SITM-MIO y cada columna mensual contiene la velocidad promedio calculada.
