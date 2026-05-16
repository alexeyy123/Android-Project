package alex.qochinyan.first;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class Product {

    public static final String EXPIRY_PENDING_SCAN = "PENDING_SCAN";

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;
    private String expiryDate;
    private boolean isExpired;
    private String barcode;
    private boolean isDeleted = false;
    private int quantity = 0;

    private String manufacturingDate;
    private String notificationDate;
    private long notificationTimestamp;

    // Firebase
    public Product() {
    }

    // Full constructor
    public Product(int id, String name, String expiryDate, boolean isExpired, String barcode, boolean isDeleted,
                   int quantity, String manufacturingDate, String notificationDate, long notificationTimestamp) {
        this.id = id;
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
        this.barcode = barcode;
        this.isDeleted = isDeleted;
        this.quantity = quantity;
        this.manufacturingDate = manufacturingDate;
        this.notificationDate = notificationDate;
        this.notificationTimestamp = notificationTimestamp;
    }

    @Ignore
    public Product(String name, String expiryDate, boolean isExpired) {
        this.name = name;
        this.expiryDate = expiryDate;
        this.isExpired = isExpired;
        this.isDeleted = false;
        this.quantity = 0;
        this.notificationTimestamp = 0L;
    }

    public int getStatusColor() {
        if (notificationTimestamp <= 0) return 0xFF757575;

        long currentTime = System.currentTimeMillis();
        long diffInMillis = notificationTimestamp - currentTime;
        long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);

        if (diffInDays <= 2) {
            return 0xFFFF0000;
        } else if (diffInDays <= 7) {
            return 0xFFFFFF00;
        } else {
            return 0xFF00FF00;
        }
    }

    public static boolean isPendingScan(String expiryDate) {
        return expiryDate != null && expiryDate.contains(EXPIRY_PENDING_SCAN);
    }

    public static int sanitizeQuantity(int raw) {
        return Math.max(0, raw);
    }

    public static int quantityForSave(int raw) {
        int s = sanitizeQuantity(raw);
        return s == 0 ? 1 : s;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(String manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public String getNotificationDate() { return notificationDate; }
    public void setNotificationDate(String notificationDate) { this.notificationDate = notificationDate; }

    public long getNotificationTimestamp() { return notificationTimestamp; }
    public void setNotificationTimestamp(long notificationTimestamp) { this.notificationTimestamp = notificationTimestamp; }
}