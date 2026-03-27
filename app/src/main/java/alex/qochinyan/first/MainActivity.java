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
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        mAuth = FirebaseAuth.getInstance();
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
        FloatingActionButton fabScan = findViewById(R.id.fabScan);

        new Thread(() -> {
            List<Product> savedProducts = db.productDao().getAllProducts();
            runOnUiThread(() -> {
                productList = new ArrayList<>(savedProducts);

                // ЛОГИКА КЛИКА ПО КАРТОЧКЕ
                adapter = new ProductAdapter(productList, product -> {
                    // Если срок еще не установлен (содержит "Ожидание")
                    if (product.getExpiryDate() != null && product.getExpiryDate().contains("Ожидание")) {
                        showDatePickerAndSetAlarm(product);
                    } else {
                        // Если уже есть срок - просто показываем инфо
                        Toast.makeText(this, "Срок: " + product.getExpiryDate() + "\nЗажмите для изменения", Toast.LENGTH_SHORT).show();
                    }
                });

                rvProducts.setLayoutManager(new LinearLayoutManager(this));
                rvProducts.setAdapter(adapter);
            });
        }).start();

        fabScan.setOnClickListener(v -> startScanner());
        setupSwipeToDelete();
    }

    // 1. ШАГ: ВЫБОР ДАТЫ
    public void showDatePickerAndSetAlarm(Product product) {
        final Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth);

            // Сразу переходим к выбору времени
            showTimePickerForDate(product, selectedDate);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.setTitle("Когда истекает срок?");
        datePickerDialog.show();
    }

    // 2. ШАГ: ВЫБОР ВРЕМЕНИ
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

                // СОХРАНЕНИЕ (В потоке, чтобы не лагало)
                new Thread(() -> {
                    try {
                        db.productDao().insert(product);
                        if (mDatabase != null) mDatabase.push().setValue(product);
                        runOnUiThread(() -> adapter.notifyDataSetChanged());
                    } catch (Exception e) { Log.e("SAVE_ERROR", e.getMessage()); }
                }).start();
            }
        }, 12, 0, true).show();
    }

    // УСТАНОВКА БУДИЛЬНИКА
    private void setAlarm(String productName, long timeInMillis) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("productName", productName);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, productName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
            } catch (Exception e) { Log.e("Alarm", "Не удалось поставить: " + e.getMessage()); }
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
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String data = response.body().string();
                        JSONObject json = new JSONObject(data);
                        runOnUiThread(() -> {
                            if (!productList.isEmpty()) {
                                Product p = productList.get(0);
                                if (json.optInt("status") == 1 && json.has("product")) {
                                    p.setName(json.optJSONObject("product").optString("product_name", "Продукт"));
                                    showDatePickerAndSetAlarm(p);
                                } else {
                                    // ЕСЛИ ТОВАР НЕ НАЙДЕН - ВВОДИМ ИМЯ
                                    showManualInputDialog(p);
                                }
                            }
                        });
                    } catch (JSONException e) { updateUI(0, "Ошибка", "JSON Error"); }
                }
            }
        });
    }

    // ВОТ ОНО - ОКНО ВВОДА НАЗВАНИЯ
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
            // СРАЗУ К ДАТЕ
            showDatePickerAndSetAlarm(p);
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            productList.remove(0);
            adapter.notifyItemRemoved(0);
        });
        builder.setCancelable(false);
        builder.show();
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
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                Product p = productList.get(pos);
                new Thread(() -> db.productDao().delete(p)).start();
                productList.remove(pos);
                adapter.notifyItemRemoved(pos);
            }
        }).attachToRecyclerView(rvProducts);
    }
}