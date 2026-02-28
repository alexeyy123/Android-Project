package alex.qochinyan.first;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id DESC")
    List<Product> getAllProducts();

    @Insert
    void insert(Product product);

    @Delete
    void delete(Product product);
}