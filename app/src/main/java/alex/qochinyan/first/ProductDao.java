package alex.qochinyan.first;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY id DESC")
    List<Product> getActiveProducts();

    @Query("SELECT * FROM products WHERE isDeleted = 1 ORDER BY id DESC")
    List<Product> getDeletedProducts();

    @Insert
    void insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);
}