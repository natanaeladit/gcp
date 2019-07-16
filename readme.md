Compile

```
mvn clean test
mvn clean package
```


Run verticle

```
java -jar target/rest-service-1.0-SNAPSHOT-fat.jar -conf src/main/conf/my-application-conf.json
```

Build and run

```
mvn package -DskipTests; java -jar target/rest-service-1.0-SNAPSHOT-fat.jar -conf src/main/conf/my-application-conf.json
```

Go to

```
http://localhost:9999/assets/index.html
```

Postgres

```
sudo -u postgres psql
CREATE DATABASE yourdbname;
CREATE USER youruser WITH ENCRYPTED PASSWORD 'yourpass';
GRANT ALL PRIVILEGES ON DATABASE yourdbname TO youruser;
```