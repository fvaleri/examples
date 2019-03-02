package it.fvaleri.spark;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class Main {
    public static void main(String[] args) {
        Logger.getLogger("org").setLevel(Level.OFF);

        SparkSession spark = SparkSession.builder().appName("EMessageStats").master("local[*]")
                .config("spark.driver.memory", "500m").config("spark.executor.memory", "6g").getOrCreate();

        // raw data loading
        spark.read().format("csv").option("sep", ",").option("inferSchema", "true").option("header", "true")
                .load(Main.class.getResource("/messaggio.csv").getPath()).createOrReplaceTempView("messaggio");

        spark.read().format("csv").option("sep", ",").option("inferSchema", "true").option("header", "true")
                .load(Main.class.getResource("/sistema-area.csv").getPath())
                .createOrReplaceTempView("sistema");

        spark.read().format("csv").option("sep", ",").option("inferSchema", "true").option("header", "true")
                .load(Main.class.getResource("/servizio.csv").getPath()).createOrReplaceTempView("servizio");

        spark.read().format("csv").option("sep", ",").option("inferSchema", "true").option("header", "true")
                .load(Main.class.getResource("/dettaglio-servizio.csv").getPath())
                .createOrReplaceTempView("dettaglio");

        // join and filtering
        Dataset<Row> joinDF = spark.sql("SELECT ME.MES_CODICE, ME.MES_MES_CODICE, ME.MES_DUPLICATO, "
                + "ME.MES_RICEZIONE, ME.MES_ELABORAZIONE, MI.SA_SIGLA, "
                + "SE.SER_DESC_BREVE, ME.MES_CLIENT, ME.MES_SERVER, ME.MES_ERRORE "
                + "FROM messaggio ME, sistema MI, dettaglio DS, servizio SE WHERE ME.MES_DS_CODICE = DS.DS_CODICE "
                + "AND DS.DS_SER_CODICE = SE.SER_CODICE AND ME.MES_MITTENTE = MI.SA_CODICE");
        joinDF.createOrReplaceTempView("dataset");

        joinDF.printSchema();
        joinDF.show();

        // computing stats
        Dataset<Row> totDF = spark.sql("SELECT count(1) FROM dataset WHERE MES_MES_CODICE IS NULL");
        System.out.println("Total processed requests: " + totDF.collectAsList().get(0).getLong(0));

        Dataset<Row> rpsDF = spark.sql(
                "SELECT cast(MES_RICEZIONE as string), count(1) as x FROM dataset WHERE MES_MES_CODICE IS NULL "
                        + "GROUP BY cast(MES_RICEZIONE as string) ORDER BY x DESC");
        System.out.println("Peak requests per second (date,rps): " + rpsDF.collectAsList().get(0));

        Dataset<Row> proDF = spark.sql("SELECT round(min(x),3), round(max(x),3), round(mean(x),3), round(stddev(x),3)"
                + "FROM (SELECT (unix_timestamp(MES_ELABORAZIONE)-unix_timestamp(MES_RICEZIONE)) as x "
                + "FROM dataset WHERE MES_MES_CODICE IS NULL AND MES_ELABORAZIONE IS NOT NULL AND MES_RICEZIONE < MES_ELABORAZIONE "
                + "AND (unix_timestamp(MES_ELABORAZIONE)-unix_timestamp(MES_RICEZIONE)) <= 10800)");
        System.out.println("Processing time in seconds (min,max,mean,std): " + proDF.collectAsList().get(0));

        Dataset<Row> srvDF = spark.sql(
                "SELECT SER_DESC_BREVE, count(1) as y, round(min(x),3), round(max(x),3), round(mean(x),3), round(stddev(x),3) "
                        + "FROM (SELECT MES_CODICE, SER_DESC_BREVE, (unix_timestamp(MES_ELABORAZIONE)-unix_timestamp(MES_RICEZIONE)) as x "
                        + "FROM dataset WHERE MES_MES_CODICE IS NULL AND MES_ELABORAZIONE IS NOT NULL "
                        + "AND MES_RICEZIONE < MES_ELABORAZIONE) GROUP BY SER_DESC_BREVE ORDER BY y DESC");
        System.out.println("Per service stats (tot,min,max,mean,std):");
        srvDF.collectAsList().stream().forEach(System.out::println);
    }
}
