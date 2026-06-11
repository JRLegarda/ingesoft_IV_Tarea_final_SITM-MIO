package org.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single GPS datagram emitted by an MIO bus.
 *
 * <p>In addition to the data fields, {@code sourceOffset} stores the byte
 * position of this line in the source file.  The concurrent version uses it
 * to filter out overlap-region datagrams that should not be counted in the
 * current chunk (only datagrams with {@code sourceOffset >= calculationStartOffset}
 * are accumulated).
 *
 * <p>Column mapping:
 * <pre>
 *   parts[0]  = eventType
 *   parts[1]  = registerdate
 *   parts[2]  = stopId
 *   column 3  = ignored legacy distance column (not used)
 *   parts[4]  = latitude  (raw int / 10_000_000)
 *   parts[5]  = longitude (raw int / 10_000_000)
 *   parts[6]  = taskId
 *   parts[7]  = lineId
 *   parts[8]  = tripId
 *   parts[9]  = unknown1
 *   parts[10] = datagramDate (timestamp for speed calculation)
 *   parts[11] = busId
 * </pre>
 */
public class Datagram {

    private final int           busId;
    private final int           stopId;
    private final int           lineId;
    private final int           tripId;
    private final double        latitud;
    private final double        longitud;
    private final LocalDateTime timestamp;

    /** Byte offset of this line in the source file (set by the reader). */
    private long sourceOffset;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parses a CSV line into a Datagram.
     *
     * @throws IllegalArgumentException if the line cannot be parsed
     */
   private final int yearMonthCode; // Añadir este campo y su getter

public Datagram(String csvLine) {
    try {
        String[] parts = csvLine.split(",");

        if (parts.length < 12) {
            throw new IllegalArgumentException("Linea incompleta");
        }

        this.busId     = Integer.parseInt(parts[11].trim());
        this.stopId    = Integer.parseInt(parts[2].trim());
        this.lineId    = Integer.parseInt(parts[7].trim());
        this.tripId    = Integer.parseInt(parts[8].trim());
        this.latitud   = Double.parseDouble(parts[4].trim()) / 10_000_000.0;
        this.longitud  = Double.parseDouble(parts[5].trim()) / 10_000_000.0;
        
        // --- PARSEO OPTIMIZADO DE FECHA ---
        String dateStr = parts[10].trim(); // "yyyy-MM-dd HH:mm:ss"
        int year  = parseSubInt(dateStr, 0, 4);
        int month = parseSubInt(dateStr, 5, 7);
        int day   = parseSubInt(dateStr, 8, 10);
        int hour  = parseSubInt(dateStr, 11, 13);
        int min   = parseSubInt(dateStr, 14, 16);
        int sec   = parseSubInt(dateStr, 17, 19);
        
        this.timestamp = LocalDateTime.of(year, month, day, hour, min, sec);
        this.yearMonthCode = year * 100 + month; // Ejemplo: 202308
        this.sourceOffset = 0L;

    } catch (Exception e) {
        throw new IllegalArgumentException("Error parseando linea: " + e.getMessage(), e);
    }
}

    // Auxiliar rápido que no genera objetos String intermediarios
    private static int parseSubInt(String str, int start, int end) {
        int result = 0;
        for (int i = start; i < end; i++) {
            result = result * 10 + (str.charAt(i) - '0');
        }
        return result;
    }

    public int getYearMonthCode() { return yearMonthCode; }

    public int           getBusId()       { return busId; }
    public int           getStopId()      { return stopId; }
    public int           getLineId()      { return lineId; }
    public int           getTripId()      { return tripId; }
    public double        getLatitud()     { return latitud; }
    public double        getLongitud()    { return longitud; }
    public LocalDateTime getTimestamp()   { return timestamp; }
    public long          getSourceOffset(){ return sourceOffset; }

    public void setSourceOffset(long sourceOffset) {
        this.sourceOffset = sourceOffset;
    }

    /** Returns true if coordinates are within valid geographic ranges and non-zero. */
    public boolean hasValidCoordinates() {
        return latitud  >= -90  && latitud  <= 90  &&
               longitud >= -180 && longitud <= 180 &&
               latitud  != 0.0  && longitud != 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "Datagram{busId=%d, lineId=%d, lat=%.6f, lon=%.6f, time=%s, offset=%d}",
                busId, lineId, latitud, longitud, timestamp, sourceOffset);
    }
}
