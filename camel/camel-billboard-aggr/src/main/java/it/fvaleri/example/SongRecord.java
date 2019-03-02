package it.fvaleri.example;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", crlf = "UNIX")
public class SongRecord {
    @DataField(pos = 1)
    private int rank;

    @DataField(pos = 2, trim = true)
    private String song;

    @DataField(pos = 3, trim = true)
    private String artist;

    @DataField(pos = 4)
    private int year;

    @DataField(pos = 5, trim = true)
    private String lyrics;

    @DataField(pos = 6)
    private String source;

    public SongRecord() {
    }

    public SongRecord(int rank, String song, String artist, int year, String lyrics, String source) {
        this.rank = rank;
        this.song = song;
        this.artist = artist;
        this.year = year;
        this.lyrics = lyrics;
        this.source = source;
    }

    public int getRank() {
        return this.rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getSong() {
        return this.song;
    }

    public void setSong(String song) {
        this.song = song;
    }

    public String getArtist() {
        return this.artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public int getYear() {
        return this.year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getLyrics() {
        return this.lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "{" +
            " rank='" + getRank() + "'" +
            ", song='" + getSong() + "'" +
            ", artist='" + getArtist() + "'" +
            ", year='" + getYear() + "'" +
            ", lyrics='" + getLyrics() + "'" +
            ", source='" + getSource() + "'" +
            "}";
    }
}
