package dev.example.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Item {

    @Id
    private String code;
    private String name;
    @Column(length = 2000)
    private String description;
    private long price;

    protected Item() {
    }

    public Item(String code, String name, String description, long price) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
}
