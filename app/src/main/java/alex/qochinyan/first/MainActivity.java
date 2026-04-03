package alex.qochinyan.first;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {
    private boolean showingActiveProducts = true;
    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private List<Product> productList;
    private AppDatabase db;
    private BottomNavigationView bottomNav;
    private final OkHttpClient client = new OkHttpClient();
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        String userId = currentUser.getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference("users").child(userId).child("products");

        db = AppDatabase.getInstance(this);
        rvProducts = findViewById(R.id.rvProducts);
        bottomNav = findViewById(R.id.bottomNav);
        FloatingActionButton fabScan = findViewById(R.id.fabScan);

        new Thread(() -> {
            List<Product> savedProducts = db.productDao().getActiveProducts();
            runOnUiThread(() -> {
                productList = new ArrayList<>(savedProducts);

                adapter = new ProductAdapter(productList, product -> {
                    if (product.getExpiryDate() != null && product.getExpiryDate().contains("Ожидание")) {
                        showDatePickerAndSetAlarm(product);
                    } else {
                        Toast.makeText(this, "Срок: " + product.getExpiryDate(), Toast.LENGTH_SHORT).show();
                    }
                });

                rvProducts.setLayoutManager(new LinearLayoutManager(this));
                rvProducts.setAdapter(adapter);
                setupSwipeToCart();
                bottomNav.setOnItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_products) {
                        showingActiveProducts = true;
                        reloadProductsFromDb(true);
                        return true;
                    }
                    if (itemId == R.id.nav_cart) {
                        showingActiveProducts = false;
                        reloadProductsFromDb(false);
                        return true;
                    }
                    return false;
                });
            });
        }).start();

        fabScan.setOnClickListener(v -> startScanner());
    }

    private void reloadProductsFromDb(boolean active) {
        new Thread(() -> {
            List<Product> list = active
                    ? db.productDao().getActiveProducts()
                    : db.productDao().getDeletedProducts();
            runOnUiThread(() -> {
                if (productList == null || adapter == null) return;
                productList.clear();
                productList.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    public void showDatePickerAndSetAlarm(Product product) {
        final Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth);
            showTimePickerForDate(product, selectedDate);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.setTitle("Когда истекает срок?");
        datePickerDialog.show();
    }

    private void showTimePickerForDate(Product product, Calendar selectedDate) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedDate.set(Calendar.MINUTE, minute);
            selectedDate.set(Calendar.SECOND, 0);

            if (selectedDate.before(Calendar.getInstance())) {
                Toast.makeText(this, "Ошибка: время уже прошло!", Toast.LENGTH_SHORT).show();
            } else {
                setAlarm(product.getName(), selectedDate.getTimeInMillis());

                String expiryText = "Годен до: " + selectedDate.get(Calendar.DAY_OF_MONTH) + "/" + (selectedDate.get(Calendar.MONTH) + 1) + "/" + selectedDate.get(Calendar.YEAR);
                product.setExpiryDate(expiryText);

                new Thread(() -> {
                    try {
                        db.productDao().insert(product);
                        if (mDatabase != null) mDatabase.push().setValue(product);
                        runOnUiThread(() -> adapter.notifyDataSetChanged());
                    } catch (Exception e) { 
                        Log.e("SAVE_ERROR", e.getMessage() != null ? e.getMessage() : "Unknown error"); 
                    }
                }).start();
            }
        }, 12, 0, true).show();
    }

    private void setAlarm(String productName, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("productName", productName);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                productName.hashCode(),
                intent,
                flags
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Разрешите уведомления в настройках", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(settingsIntent);
                return;
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        } catch (SecurityException e) {
            Log.e("Alarm", "Ошибка безопасности: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("food_guard_channel", "FoodGuard", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startScanner() {
        GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener(barcode -> {
                    String code = barcode.getRawValue();
                    if (code != null) {
                        if (!showingActiveProducts) {
                            Toast.makeText(this, "Откройте вкладку «Продукты» для сканирования", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (productList == null || adapter == null) return;
                        Product newProduct = new Product("Поиск...", "Ожидание...", false);
                        newProduct.setBarcode(code);
                        productList.add(0, newProduct);
                        adapter.notifyItemInserted(0);
                        fetchProductInfo(code);
                    }
                });
    }

    private void fetchProductInfo(String code) {
        String url = "https://world.openfoodfacts.org/api/v0/product/" + code + ".json";
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { updateUI(0, "Ошибка сети", "Нет данных"); }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful() && responseBody != null) {
                        String data = responseBody.string();
                        JSONObject json = new JSONObject(data);
                        runOnUiThread(() -> {
                            if (productList != null && !productList.isEmpty()) {
                                Product p = productList.get(0);
                                if (json.optInt("status") == 1 && json.has("product")) {
                                    JSONObject productJson = json.optJSONObject("product");
                                    if (productJson != null) {
                                        p.setName(productJson.optString("product_name", "Продукт"));
                                    }
                                    showDatePickerAndSetAlarm(p);
                                } else {
                                    showManualInputDialog(p);
                                }
                            }
                        });
                    }
                } catch (JSONException e) { 
                    updateUI(0, "Ошибка", "JSON Error"); 
                }
            }
        });
    }

    private void showManualInputDialog(Product p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Продукт не найден");
        builder.setMessage("Введите название вручную:");

        final EditText input = new EditText(this);
        input.setHint("Название товара");
        builder.setView(input);

        builder.setPositiveButton("Далее", (dialog, which) -> {
            String name = input.getText().toString().trim();
            p.setName(name.isEmpty() ? "Свой продукт" : name);
            showDatePickerAndSetAlarm(p);
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            if (productList != null && !productList.isEmpty()) {
                productList.remove(0);
                adapter.notifyItemRemoved(0);
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void updateUI(int pos, String name, String date) {
        runOnUiThread(() -> {
            if (productList != null && productList.size() > pos) {
                productList.get(pos).setName(name);
                productList.get(pos).setExpiryDate(date);
                adapter.notifyItemChanged(pos);
            }
        });
    }

    private void setupSwipeToCart() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (showingActiveProducts) {
                    return ItemTouchHelper.RIGHT;
                } else {
                    return ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || productList == null || adapter == null) {
                    return;
                }
                final Product p = productList.get(pos);
                final int swipedPos = pos;

                if (showingActiveProducts) {
                    if (direction == ItemTouchHelper.RIGHT) {
                        p.setDeleted(true);
                        new Thread(() -> {
                            db.productDao().update(p);
                            runOnUiThread(() -> {
                                if (productList == null || adapter == null) return;
                                if (swipedPos < 0 || swipedPos >= productList.size()) return;
                                productList.remove(swipedPos);
                                adapter.notifyItemRemoved(swipedPos);
                                Toast.makeText(MainActivity.this, "Перенесено в корзину", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    }
                } else {
                    if (direction == ItemTouchHelper.LEFT) {
                        p.setDeleted(false);
                        new Thread(() -> {
                            db.productDao().update(p);
                            runOnUiThread(() -> {
                                if (productList == null || adapter == null) return;
                                if (swipedPos < 0 || swipedPos >= productList.size()) return;
                                productList.remove(swipedPos);
                                adapter.notifyItemRemoved(swipedPos);
                                Toast.makeText(MainActivity.this, "Восстановлено в продукты", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    } else if (direction == ItemTouchHelper.RIGHT) {
                        new Thread(() -> {
                            db.productDao().delete(p);
                            runOnUiThread(() -> {
                                if (productList == null || adapter == null) return;
                                if (swipedPos < 0 || swipedPos >= productList.size()) return;
                                productList.remove(swipedPos);
                                adapter.notifyItemRemoved(swipedPos);
                                Toast.makeText(MainActivity.this, "Удалено навсегда", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    }
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvProducts);
    }
}
