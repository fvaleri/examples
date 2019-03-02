package it.fvaleri.example.model;

public class Search {
    int userId;
    String searchTerms;

    public Search(int userId, String searchTerms) {
        this.userId = userId;
        this.searchTerms = searchTerms;
    }

    public int getUserId() {
        return userId;
    }

    public String getSearchTerms() {
        return searchTerms;
    }
}
