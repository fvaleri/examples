```sh
psql -d postgres -U postgres
DROP DATABASE IF EXISTS test_database;
CREATE DATABASE test_database WITH TEMPLATE template0 ENCODING UTF8;
CREATE USER test_user WITH ENCRYPTED PASSWORD 'changeit';
GRANT CONNECT ON DATABASE test_database TO test_user;
GRANT USAGE ON SCHEMA public TO test_user;
\c test_database
CREATE TABLE test_table1 (value INTEGER NOT NULL);
CREATE TABLE test_table2 (value INTEGER NOT NULL);
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO test_user;

mvn compile exec:java
```
