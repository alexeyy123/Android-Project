package alex.qochinyan.first;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY CASE WHEN notificationTimestamp = 0 THEN 1 ELSE 0 END, notificationTimestamp ASC")
    List<Product> getActiveProducts();

    @Query("SELECT * FROM products WHERE isDeleted = 0")
    List<Product> getAllActive();

    @Query("SELECT * FROM products WHERE isDeleted = 1 ORDER BY id DESC")
    List<Product> getDeletedProducts();

    /** Case-insensitive match so "Coca-Cola" merges with "coca-cola" (no duplicate rows). */
    @Query("SELECT * FROM products WHERE isDeleted = 0 AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    Product findActiveByName(String name);

    // ИСПРАВЛЕНО: Добавлен onConflict = OnConflictStrategy.REPLACE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    @Query("SELECT COUNT(*) FROM products WHERE isDeleted = 0")
    int countActiveInventory();

    @Query("SELECT COUNT(*) FROM products WHERE isDeleted = 0 AND notificationTimestamp > :nowMillis AND notificationTimestamp <= :endMillis")
    int countExpiringSoon(long nowMillis, long endMillis);

    @Query("DELETE FROM products WHERE isDeleted = 1")
    void clearDeletedProducts();

    // ДОПОЛНИТЕЛЬНО: Метод для обновления или вставки (удобно)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(Product product);
}