package it.fvaleri.example;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import javax.persistence.Entity;

@Entity
public class BookEntity extends PanacheEntity {
    public String title;
    public String category;
}
