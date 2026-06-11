package org.example.report;

public class ActiveLine {
    private final int lineId;
    private final String shortName;
    private final String description;

    public ActiveLine(int lineId, String shortName, String description) {
        this.lineId = lineId;
        this.shortName = shortName;
        this.description = description;
    }

    public int getLineId() {
        return lineId;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }
}
