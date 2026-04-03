package alex.qochinyan.first;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class Product {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;
    private String expiryDate;
    private boolean isExpired;
    private String barcode; // Поле для штрих-кода
    private boolean isDeleted = false;

    // 1. ПУСТОЙ КОНСТРУКТОР (Нужен для Firebase, чтобы он мог создать объект)
    public Product() {
    }

    // Конструктор для Room (с ID)
    public Product(int id, String name, String expiryDate, boolean isExpired, String barcode, boolean isDeleted) {
        this.id = id;
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
        this.barcode = barcode;
        this.isDeleted = isDeleted;
    }

    // 2. ОБНОВЛЕННЫЙ КОНСТРУКТОР ДЛЯ СКАНЕРА
    @Ignore
    public Product(String name, String expiryDate, boolean isExpired) {
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
        this.isDeleted = false;
    }

    // ГЕТТЕРЫ И СЕТТЕРЫ
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }

    // 3. НОВЫЕ МЕТОДЫ ДЛЯ ШТРИХ-КОДА (чтобы MainActivity не ругалась)
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}