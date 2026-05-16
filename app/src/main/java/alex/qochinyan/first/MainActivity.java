package alex.qochinyan.first;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyBeforeActivityCreate(getApplicationContext());
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        setContentView(R.layout.activity_main);

        String userId = (currentUser != null) ? currentUser.getUid() : "guest_user";
        db = AppDatabase.getInstance(getApplicationContext());

        if (currentUser != null) {
            mDatabase = FirebaseDatabase.getInstance().getReference("users").child(userId).child("products");
        } else {
            mDatabase = null;
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        // --- ВОТ ЭТОТ КУСОК ОБНОВИЛСЯ ---
        if (fabAdd != null) {
            // Обычный клик
            fabAdd.setOnClickListener(v -> startManualAdd());

            // Длинный клик (Микрофон)
            fabAdd.setOnLongClickListener(v -> {
                startVoiceInput();
                return true;
            });
        }
        // --------------------------------

        createNotificationChannel();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inventory) showFragment(new InventoryFragment());
            else if (id == R.id.nav_cart) showFragment(new CartFragment());
            else if (id == R.id.nav_profile) showFragment(new ProfileFragment());
            return true;
        });

        if (savedInstanceState == null) {
            showFragment(new InventoryFragment());
            bottomNav.setSelectedItemId(R.id.nav_inventory);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void showFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void startManualAdd() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый продукт");
        final EditText input = new EditText(this);
        input.setHint("Название");
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(60, 20, 60, 0);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Далее", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                Product p = new Product();
                p.setName(name);
                showQuantityDialog(p);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showQuantityDialog(Product p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Количество");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("1");
        builder.setView(input);
        builder.setPositiveButton("Далее", (dialog, which) -> {
            String q = input.getText().toString();
            p.setQuantity(q.isEmpty() ? 1 : Integer.parseInt(q));
            beginProductDateSetup(p);
        });
        builder.show();
    }

    /**
     * Показывает цепочку выбора дат и времени.
     * Сделан public для вызова из InventoryFragment.
     */
    public void beginProductDateSetup(Product p) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar exp = Calendar.getInstance();
            exp.set(y, m, d);
            p.setExpiryDate(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(exp.getTime()));

            new TimePickerDialog(this, (v2, h, min) -> {
                Calendar notif = Calendar.getInstance();
                notif.set(y, m, d, h, min);
                p.setNotificationDate(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(notif.getTime()));
                p.setNotificationTimestamp(notif.getTimeInMillis());
                persistProduct(p);
            }, 9, 0, true).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Синхронизирует продукт с Firebase.
     * Сделан public для вызова из InventoryFragment.
     */
    public void syncProductToFirebase(Product p) {
        if (mDatabase != null) {
            mDatabase.child(String.valueOf(p.getId())).setValue(p);
        }
    }

    private void persistProduct(Product p) {
        new Thread(() -> {
            long id;
            if (p.getId() == 0) {
                id = db.productDao().insert(p);
                p.setId((int) id);
            } else {
                id = p.getId();
            }

            setAlarm(p.getName(), p.getNotificationTimestamp(), (int) id);

            runOnUiThread(() -> {
                syncProductToFirebase(p);
                InventoryFragment inv = (InventoryFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container);
                if (inv != null) inv.reloadFromDb();
                Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void setAlarm(String name, long time, int id) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NotificationReceiver.class).putExtra("productName", name);
        PendingIntent pi = PendingIntent.getBroadcast(this, Math.abs(id), intent,
                PendingIntent.FLAG_IMMUTABLE);

        if (am == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
            } else {
                requestExactAlarmPermission();
                am.set(AlarmManager.RTC_WAKEUP, time, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        }
    }
    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel("food_guard_channel", "FoodGuard", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }
    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ГОЛОСА ---
    private void startVoiceInput() {
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Назовите продукт...");
        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "Голосовой ввод недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> result = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                Product p = new Product();
                p.setName(spokenText);
                beginProductDateSetup(p);
            }
        }
    }
}
