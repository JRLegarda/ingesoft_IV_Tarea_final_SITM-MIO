package org.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single GPS datagram emitted by an MIO bus.
 * Column mapping matches the data dictionary (Diccionario_De_Datos-OkGTM.pdf):
 *   parts[0]  = eventType
 *   parts[1]  = registerdate
 *   parts[2]  = stopId
 *   column 3  = ignored legacy distance column (not used)
 *   parts[4]  = latitude  (raw integer, divide by 10_000_000)
 *   parts[5]  = longitude (raw integer, divide by 10_000_000)
 *   parts[6]  = taskId
 *   parts[7]  = lineId
 *   parts[8]  = tripId
 *   parts[9]  = unknown1
 *   parts[10] = datagramDate (timestamp used for speed calculation)
 *   parts[11] = busId
 */
public class Datagram {

    private final int busId;
    private final int stopId;
    private final int lineId;
    private final int tripId;
    private final double latitud;
    private final double longitud;
    private final LocalDateTime timestamp;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parses a CSV line into a Datagram.
     *
     * @param csvLine a raw line from the datagram CSV file
     * @throws IllegalArgumentException if the line cannot be parsed
     */
    public Datagram(String csvLine) {
        try {
            String[] parts = csvLine.split(",");

            if (parts.length < 12) {
                throw new IllegalArgumentException(
                        "Linea incompleta: esperadas 12 columnas, encontradas " + parts.length);
            }

            this.busId     = Integer.parseInt(parts[11].trim());
            this.stopId    = Integer.parseInt(parts[2].trim());
            this.lineId    = Integer.parseInt(parts[7].trim());
            this.tripId    = Integer.parseInt(parts[8].trim());
            this.latitud   = Double.parseDouble(parts[4].trim()) / 10_000_000.0;
            this.longitud  = Double.parseDouble(parts[5].trim()) / 10_000_000.0;
            this.timestamp = LocalDateTime.parse(parts[10].trim(), DATE_FORMATTER);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error parseando numeros: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Linea invalida: " + e.getMessage(), e);
        }
    }

    public int getBusId()            { return busId; }
    public int getStopId()           { return stopId; }
    public int getLineId()           { return lineId; }
    public int getTripId()           { return tripId; }
    public double getLatitud()       { return latitud; }
    public double getLongitud()      { return longitud; }
    public LocalDateTime getTimestamp() { return timestamp; }

    /**
     * Returns true if the coordinates are within valid geographic ranges
     * and are not zero (default/sentinel values).
     */
    public boolean hasValidCoordinates() {
        return latitud  >= -90  && latitud  <= 90  &&
               longitud >= -180 && longitud <= 180 &&
               latitud  != 0.0  && longitud != 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "Datagram{busId=%d, lineId=%d, lat=%.6f, lon=%.6f, time=%s}",
                busId, lineId, latitud, longitud, timestamp);
    }
}
