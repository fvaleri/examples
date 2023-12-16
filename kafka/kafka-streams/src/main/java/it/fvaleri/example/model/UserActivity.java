package it.fvaleri.example.model;

public class UserActivity {
    int userId;
    String userName;
    String zipcode;
    String[] interests;
    String searchTerm;
    String page;

    public UserActivity(int userId, String userName, String zipcode, String[] interests, String searchTerm, String page) {
        this.userId = userId;
        this.userName = userName;
        this.zipcode = zipcode;
        this.interests = interests;
        this.searchTerm = searchTerm;
        this.page = page;
    }

    public UserActivity updateSearch(String searchTerm) {
        this.searchTerm = searchTerm;
        return this;
    }
}
