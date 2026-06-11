package org.example.model;

public class Datagram {
    private int busId;
    private int stopId;
    private int lineId;
    private int tripId;
    private double latitud;
    private double longitud;
    private double latRad;
    private double lonRad;
    private double cosLat;
    private long timestampSeconds;
    private int month;
    private int yearMonth;
    private long sourceOffset;
    private final boolean poison;

    public Datagram(boolean poison) {
        this.poison = poison;
    }

    public Datagram(String csvLine) {
        this.poison = false;
        try {
            int len = csvLine.length();
            int idx = 0;
            int start = 0;

            int currentStopId = 0, currentLineId = 0, currentTripId = 0, currentBusId = 0;
            double currentLat = 0, currentLon = 0;
            int dateStart = 0;
            int dateEnd = 0;

            for (int i = 0; i <= len; i++) {
                if (i == len || csvLine.charAt(i) == ',') {
                    if (idx == 2) currentStopId = parseIntFast(csvLine, start, i);
                    else if (idx == 4) currentLat = parseDoubleFast(csvLine, start, i) / 10000000.0;
                    else if (idx == 5) currentLon = parseDoubleFast(csvLine, start, i) / 10000000.0;
                    else if (idx == 7) currentLineId = parseIntFast(csvLine, start, i);
                    else if (idx == 8) currentTripId = parseIntFast(csvLine, start, i);
                    else if (idx == 10) {
                        dateStart = start;
                        dateEnd = i;
                    } else if (idx == 11) {
                        currentBusId = parseIntFast(csvLine, start, i);
                        break;
                    }
                    idx++;
                    start = i + 1;
                }
            }

            this.stopId = currentStopId;
            this.latitud = currentLat;
            this.longitud = currentLon;
            this.latRad = Math.toRadians(currentLat);
            this.lonRad = Math.toRadians(currentLon);
            this.cosLat = Math.cos(latRad);
            this.lineId = currentLineId;
            this.tripId = currentTripId;
            this.busId = currentBusId;

            while (dateStart < dateEnd && csvLine.charAt(dateStart) <= ' ') {
                dateStart++;
            }
            while (dateEnd > dateStart && csvLine.charAt(dateEnd - 1) <= ' ') {
                dateEnd--;
            }

            if (dateStart > 0 && (dateEnd - dateStart) >= 19) {
                int year = parseIntFast(csvLine, dateStart, dateStart + 4);
                int parsedMonth = parseIntFast(csvLine, dateStart + 5, dateStart + 7);
                int day = parseIntFast(csvLine, dateStart + 8, dateStart + 10);
                int hour = parseIntFast(csvLine, dateStart + 11, dateStart + 13);
                int minute = parseIntFast(csvLine, dateStart + 14, dateStart + 16);
                int second = parseIntFast(csvLine, dateStart + 17, dateStart + 19);
                this.month = parsedMonth;
                this.yearMonth = year * 100 + parsedMonth;
                this.timestampSeconds = toEpochSeconds(year, parsedMonth, day, hour, minute, second);
            } else {
                throw new IllegalArgumentException("Fecha invalida");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parseando linea: " + e.getMessage());
        }
    }

    private int parseIntFast(String s, int start, int end) {
        int num = 0;
        boolean sign = false;
        int i = start;
        while (i < end && s.charAt(i) <= ' ') i++;
        if (i < end && s.charAt(i) == '-') {
            sign = true;
            i++;
        }
        while (i < end && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            num = num * 10 + (s.charAt(i) - '0');
            i++;
        }
        return sign ? -num : num;
    }

    private double parseDoubleFast(String s, int start, int end) {
        return (double) parseIntFast(s, start, end);
    }

    private long toEpochSeconds(int year, int month, int day, int hour, int minute, int second) {
        long epochDay = daysFromCivil(year, month, day);
        return epochDay * 86_400L + hour * 3_600L + minute * 60L + second;
    }

    private long daysFromCivil(int year, int month, int day) {
        year -= month <= 2 ? 1 : 0;
        long era = Math.floorDiv(year, 400);
        int yoe = (int) (year - era * 400);
        int doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097L + doe - 719468L;
    }

    public boolean isPoisonPill() { return poison; }
    public int getBusId() { return busId; }
    public int getStopId() { return stopId; }
    public int getLineId() { return lineId; }
    public int getTripId() { return tripId; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public double getLatRad() { return latRad; }
    public double getLonRad() { return lonRad; }
    public double getCosLat() { return cosLat; }
    public long getTimestampSeconds() { return timestampSeconds; }
    public int getMonth() { return month; }
    public int getYearMonth() { return yearMonth; }
    public long getSourceOffset() { return sourceOffset; }
    public void setSourceOffset(long sourceOffset) { this.sourceOffset = sourceOffset; }

    public boolean hasValidCoordinates() {
        return latitud >= -90 && latitud <= 90
                && longitud >= -180 && longitud <= 180
                && latitud != 0 && longitud != 0;
    }
}
