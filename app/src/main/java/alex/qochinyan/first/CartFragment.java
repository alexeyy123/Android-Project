package alex.qochinyan.first;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CartFragment extends Fragment {
    private android.hardware.SensorManager sensorManager;
    private float acceleration;
    private float currentAcceleration;
    private float lastAcceleration;

    private RecyclerView rvCart;
    private ProductAdapter adapter;
    private final List<Product> cartList = new ArrayList<>();
    private AppDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_cart, container, false);
        db = AppDatabase.getInstance(requireContext().getApplicationContext());
        rvCart = v.findViewById(R.id.rvCart);

        // --- ИНИЦИАЛИЗАЦИЯ СЕНСОРОВ ДЛЯ ВСТРЯСКИ ---
        sensorManager = (android.hardware.SensorManager) requireContext().getSystemService(android.content.Context.SENSOR_SERVICE);
        acceleration = 10f;
        currentAcceleration = android.hardware.SensorManager.GRAVITY_EARTH;
        lastAcceleration = android.hardware.SensorManager.GRAVITY_EARTH;
        // ------------------------------------------

        // Логика кнопки очистки (оставляем, пусть будет и кнопка, и встряска)
        View btnClear = v.findViewById(R.id.tvClearCart);
        if (btnClear != null) {
            btnClear.setOnClickListener(view -> performShakeClear());
        }

        adapter = new ProductAdapter(cartList,
                product -> Toast.makeText(requireContext(), product.getName(), Toast.LENGTH_SHORT).show(),
                null);

        rvCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCart.setAdapter(adapter);
        setupSwipe();

        return v;
    }
    @Override
    public void onResume() {
        super.onResume();
        // 1. Загружаем данные
        reloadFromDb();
        // 2. Включаем микрофон для встряски
        if (sensorManager != null) {
            sensorManager.registerListener(sensorListener,
                    sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER),
                    android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        // Выключаем датчик, когда уходим с экрана, чтобы не тратить батарею
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        super.onPause();
    }

    // --- ЛОГИКА ВСТРЯСКИ (ОБРАБОТЧИК) ---
    private final android.hardware.SensorEventListener sensorListener = new android.hardware.SensorEventListener() {
        @Override
        public void onSensorChanged(android.hardware.SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            lastAcceleration = currentAcceleration;
            currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = currentAcceleration - lastAcceleration;
            acceleration = acceleration * 0.9f + delta;

            // Если тряхнули достаточно сильно
            if (acceleration > 12) {
                if (!cartList.isEmpty()) {
                    performShakeClear();
                }
            }
        }

        @Override
        public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
    };

    // Метод, который вызывается и кнопкой, и встряской
    private void performShakeClear() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Очистить корзину?")
                .setMessage("Вы встряхнули устройство. Удалить все продукты навсегда?")
                .setPositiveButton("Да", (dialog, which) -> {
                    new Thread(() -> {
                        db.productDao().clearDeletedProducts();
                        postIfAlive(() -> {
                            cartList.clear();
                            adapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), "Корзина очищена!", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void postIfAlive(@NonNull Runnable action) {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) action.run();
        });
    }

    public void reloadFromDb() {
        new Thread(() -> {
            List<Product> deleted = db.productDao().getDeletedProducts();
            postIfAlive(() -> {
                cartList.clear();
                cartList.addAll(deleted);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Product p = cartList.get(pos);
                final int swipedPos = pos;

                if (direction == ItemTouchHelper.LEFT) {
                    p.setDeleted(false);
                    new Thread(() -> {
                        db.productDao().update(p);
                        postIfAlive(() -> {
                            if (swipedPos < 0 || swipedPos >= cartList.size()) return;
                            cartList.remove(swipedPos);
                            adapter.notifyItemRemoved(swipedPos);
                            Toast.makeText(requireContext(), "Restored to inventory", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                } else if (direction == ItemTouchHelper.RIGHT) {
                    new Thread(() -> {
                        db.productDao().delete(p);
                        postIfAlive(() -> {
                            if (swipedPos < 0 || swipedPos >= cartList.size()) return;
                            cartList.remove(swipedPos);
                            adapter.notifyItemRemoved(swipedPos);
                            Toast.makeText(requireContext(), "Deleted permanently", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                }
            }
        }).attachToRecyclerView(rvCart);
    }
}