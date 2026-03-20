package alex.qochinyan.first;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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

    // ВОТ ЭТИ СТРОКИ ИСПРАВЛЯЮТ ТВОИ КРАСНЫЕ ОШИБКИ
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Инициализация Auth и проверка юзера
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // 2. Инициализация базы ПЕРСОНАЛЬНО для юзера
        String userId = currentUser.getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference("users").child(userId).child("products");

        db = AppDatabase.getInstance(this);
        rvProducts = findViewById(R.id.rvProducts);
        FloatingActionButton fabScan = findViewById(R.id.fabScan);

        // Загрузка из локальной базы Room
        new Thread(() -> {
            List<Product> savedProducts = db.productDao().getAllProducts();
            runOnUiThread(() -> {
                productList = new ArrayList<>(savedProducts);
                adapter = new ProductAdapter(productList);
                rvProducts.setLayoutManager(new LinearLayoutManager(this));
                rvProducts.setAdapter(adapter);
            });
        }).start();

        fabScan.setOnClickListener(v -> startScanner());
        setupSwipeToDelete();
    }

    private void startScanner() {
        GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener(barcode -> {
                    String code = barcode.getRawValue();
                    if (code != null) {
                        Product newProduct = new Product("Searching...", "Waiting...", false);
                        newProduct.setBarcode(code);
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
                updateUI(0, "Network Error", "No Internet");
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
                                    showDatePickerAndSave(p);
                                } else {
                                    showManualInputDialog(p, code);
                                }
                            }
                        });
                    } catch (Exception e) {
                        updateUI(0, "Data Error", "API Error");
                    }
                }
            }
        });
    }

    private void showManualInputDialog(Product p, String code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Product Not Found");
        builder.setMessage("Enter product name:");
        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Next", (dialog, which) -> {
            String manualName = input.getText().toString();
            p.setName(manualName.isEmpty() ? "Custom Item" : manualName);
            showDatePickerAndSave(p);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            productList.remove(0);
            adapter.notifyItemRemoved(0);
        });
        builder.show();
    }

    private void showDatePickerAndSave(Product p) {
        final Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            String selectedDate = "Expires: " + day + "/" + (month + 1) + "/" + year;
            p.setExpiryDate(selectedDate);

            new Thread(() -> db.productDao().insert(p)).start();
            saveToFirebase(p);
            adapter.notifyItemChanged(0);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.setTitle("Select Expiry Date");
        datePickerDialog.show();
    }

    private void saveToFirebase(Product p) {
        String firebaseKey = mDatabase.push().getKey();
        if (firebaseKey != null) {
            mDatabase.child(firebaseKey).setValue(p);
        }
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

                // Удаление из Firebase
                mDatabase.orderByChild("name").equalTo(p.getName()).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            child.getRef().removeValue();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });

                productList.remove(position);
                adapter.notifyItemRemoved(position);
            }
        }).attachToRecyclerView(rvProducts);
    }
}