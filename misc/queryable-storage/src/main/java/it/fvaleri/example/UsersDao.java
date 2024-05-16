package it.fvaleri.example;

import it.fvaleri.example.storage.QueryableStorage;
import it.fvaleri.example.storage.QueryableStorage.Row;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public class UsersDao {
    private QueryableStorage storage;

    public UsersDao(QueryableStorage storage) {
        this.storage = storage;
    }

    public int insert(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Invalid user");
        }
        return storage.write("users.insert", List.of(user.userid(), user.password(), user.email()));
    }

    public User findByPk(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Invalid key");
        }
        List<Row> rows = storage.read("users.select.by.pk", List.of(String.class, String.class, String.class), List.of(key));
        if (rows.size() == 0) {
            throw new RuntimeException(format("User %s not found", key));
        }
        return new User(
            (String) rows.get(0).columns().get(0),
            (String) rows.get(0).columns().get(1),
            (String) rows.get(0).columns().get(2)
        );
    }

    public List<User> findAll() {
        List<User> result = new ArrayList<>();
        List<Row> rows = storage.read("users.select.all", List.of(String.class, String.class, String.class));
        rows.forEach(row -> result.add(new User(
            (String) row.columns().get(0),
            (String) row.columns().get(1),
            (String) row.columns().get(2)
        )));
        return result;
    }

    public int update(User user) {
        if (user == null || user.userid() == null) {
            throw new IllegalArgumentException("Invalid user");
        }
        return storage.write("users.update", List.of(user.password(), user.email(), user.userid()));
    }

    public int delete(String userid) {
        if (userid == null) {
            throw new IllegalArgumentException("Invalid userid");
        }
        return storage.write("users.delete", List.of(userid));
    }

    public static class User {
        private String userid;
        private String password;
        private String email;

        public User(String userid, String password, String email) {
            this.userid = userid;
            this.password = sha256(password);
            this.email = email;
        }

        public String userid() {
            return userid;
        }

        public void userid(String userid) {
            this.userid = userid;
        }

        public String password() {
            return password;
        }

        public void password(String password) {
            this.password = sha256(password);
        }

        public String email() {
            return email;
        }

        public void email(String email) {
            this.email = email;
        }

        public static String sha256(String base) {
            try{
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(base.getBytes("UTF-8"));
                StringBuilder hexString = new StringBuilder();
                for (int i = 0; i < hash.length; i++) {
                    String hex = Integer.toHexString(0xff & hash[i]);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch(Exception ex){
                throw new RuntimeException(ex);
            }
        }

        @Override
        public String toString() {
            return "User{" +
                "userid='" + userid + '\'' +
                ", password='" + password + '\'' +
                ", email='" + email + '\'' +
                '}';
        }
    }
}
