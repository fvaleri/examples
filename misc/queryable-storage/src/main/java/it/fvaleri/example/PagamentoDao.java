package it.fvaleri.example;

import it.fvaleri.example.storage.QueryableStorage;
import it.fvaleri.example.storage.QueryableStorage.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PagamentoDao {
    private QueryableStorage storage;
    private int batchSize;

    public PagamentoDao(QueryableStorage storage, int batchSize) {
        this.storage = storage;
        this.batchSize = batchSize;
    }

    public int insert(Pagamento pagamento) {
        if (pagamento == null) {
            throw new IllegalArgumentException("Invalid pagamento");
        }
        return storage.write(
            "pagamento.insert",
            List.of(
                pagamento.pagCodice(),
                pagamento.pagIntCodice(),
                pagamento.pagImporto(),
                pagamento.pagDataPagamento(),
                pagamento.pagCpCodice(),
                pagamento.pagStato(),
                pagamento.pagCc(),
                pagamento.pagSisareTipo()
            ),
            batchSize
        );
    }

    public Pagamento findByPk(long key) {
        if (key == 0) {
            throw new IllegalArgumentException("Invalid key");
        }
        List<Row> rows = storage.read(
            "pagamento.select.by.pk",
            List.of(Long.class, Long.class, BigDecimal.class, LocalDate.class, Long.class, Long.class, String.class, String.class),
            List.of(String.valueOf(key))
        );
        if (rows.size() == 0) {
            return null;
        }
        return new Pagamento(
            (Long) rows.get(0).columns().get(0),
            (Long) rows.get(0).columns().get(1),
            (BigDecimal) rows.get(0).columns().get(2),
            (LocalDate) rows.get(0).columns().get(3),
            (Long) rows.get(0).columns().get(4),
            (Long) rows.get(0).columns().get(5),
            (String) rows.get(0).columns().get(6),
            (String) rows.get(0).columns().get(7)
        );
    }

    public int update(Pagamento pagamento) {
        if (pagamento == null) {
            throw new IllegalArgumentException("Invalid pagamento");
        }
        return storage.write(
            "pagamento.update",
            List.of(
                pagamento.pagIntCodice(),
                pagamento.pagImporto(),
                pagamento.pagDataPagamento(),
                pagamento.pagCpCodice(),
                pagamento.pagStato(),
                pagamento.pagCc(),
                pagamento.pagSisareTipo(),
                pagamento.pagCodice()
            ),
            batchSize
        );
    }

    record Pagamento(long pagCodice, long pagIntCodice, BigDecimal pagImporto, LocalDate pagDataPagamento,
                     long pagCpCodice, long pagStato, String pagCc, String pagSisareTipo) { }
}
