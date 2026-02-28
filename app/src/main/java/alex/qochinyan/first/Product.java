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


    public Product(int id, String name, String expiryDate, boolean isExpired) {
        this.id = id;
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
    }


    @Ignore
    public Product(String name, String expiryDate, boolean isExpired) {
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }
}