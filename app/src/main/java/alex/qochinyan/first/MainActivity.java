package alex.qochinyan.first;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private List<Product> productList;
    private AppDatabase db;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация базы данных и UI
        db = AppDatabase.getInstance(this);
        rvProducts = findViewById(R.id.rvProducts);
        FloatingActionButton fabScan = findViewById(R.id.fabScan);

        // Загрузка данных из Room
        productList = new ArrayList<>(db.productDao().getAllProducts());
        adapter = new ProductAdapter(productList);
        rvProducts.setLayoutManager(new LinearLayoutManager(this));
        rvProducts.setAdapter(adapter);

        fabScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScanner();
            }
        });

        setupSwipeToDelete();
    }

    private void startScanner() {
        GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener(barcode -> {
                    String code = barcode.getRawValue();
                    if (code != null) {
                        // Временный статус поиска
                        Product newProduct = new Product("Searching...", "Waiting for data...", false);
                        productList.add(0, newProduct);
                        adapter.notifyItemInserted(0);
                        rvProducts.scrollToPosition(0);
                        fetchProductInfo(code);
                    }
                })
                .addOnFailureListener(e -> Log.e("Scanner", "Error: " + e.getMessage()));
    }

    private void fetchProductInfo(String code) {
        String url = "https://world.openfoodfacts.org/api/v0/product/" + code + ".json";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                updateUI(0, "Network Error", "No Internet Connection");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);

                        runOnUiThread(() -> {
                            if (!productList.isEmpty()) {
                                Product p = productList.get(0);
                                if (json.optInt("status") == 1 && json.has("product")) {
                                    JSONObject productData = json.optJSONObject("product");
                                    String name = productData.optString("product_name", "Unknown Item");
                                    p.setName(name);
                                    // После нахождения имени — спрашиваем дату
                                    showDatePickerAndSave(p);
                                } else {
                                    // Если в базе нет — ручной ввод имени, потом даты
                                    showManualInputDialog(p, code);
                                }
                            }
                        });
                    } catch (Exception e) {
                        updateUI(0, "Data Error", "API Response Error");
                    }
                }
            }
        });
    }

    private void showManualInputDialog(Product p, String code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Product Not Found");
        builder.setMessage("Please enter product name:");

        final EditText input = new EditText(this);
        input.setHint("e.g. Milk, Bread, Juice");
        builder.setView(input);

        builder.setPositiveButton("Next", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String manualName = input.getText().toString();
                p.setName(manualName.isEmpty() ? "Custom Item" : manualName);
                showDatePickerAndSave(p);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            productList.remove(0);
            adapter.notifyItemRemoved(0);
        });

        builder.show();
    }

    private void showDatePickerAndSave(Product p) {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, monthOfYear, dayOfMonth) -> {
            String selectedDate = "Expires: " + dayOfMonth + "/" + (monthOfYear + 1) + "/" + year1;
            p.setExpiryDate(selectedDate);

            // Сохраняем в БД только когда есть и имя, и дата
            new Thread(() -> db.productDao().insert(p)).start();
            adapter.notifyItemChanged(0);

        }, year, month, day);

        datePickerDialog.setTitle("Select Expiry Date");
        datePickerDialog.show();
    }

    private void updateUI(int pos, String name, String date) {
        runOnUiThread(() -> {
            if (productList.size() > pos) {
                productList.get(pos).setName(name);
                productList.get(pos).setExpiryDate(date);
                adapter.notifyItemChanged(pos);
            }
        });
    }

    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Product p = productList.get(position);
                new Thread(() -> db.productDao().delete(p)).start();
                productList.remove(position);
                adapter.notifyItemRemoved(position);
            }
        }).attachToRecyclerView(rvProducts);
    }
}