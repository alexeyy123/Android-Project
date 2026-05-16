package alex.qochinyan.first;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class InventoryFragment extends Fragment {

    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private final List<Product> productList = new ArrayList<>();
    private final List<Product> fullList = new ArrayList<>();
    private AppDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_inventory, container, false);
        db = AppDatabase.getInstance(requireContext().getApplicationContext());
        rvProducts = v.findViewById(R.id.rvProducts);


        adapter = new ProductAdapter(productList,
                product -> {
                    MainActivity act = mainActivity();
                    if (act != null) act.beginProductDateSetup(product);
                },
                product -> {
                    MainActivity act = mainActivity();
                    if (act != null) act.beginProductDateSetup(product);
                });

        adapter.setOnDeleteListener(this::onSwipeDeleteToCart);
        rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvProducts.setAdapter(adapter);


        SearchView searchView = v.findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });

        setupSwipeReveal();
        reloadFromDb();

        return v;
    }


    private void filter(String text) {
        List<Product> filteredList = new ArrayList<>();
        for (Product item : fullList) {
            if (item.getName().toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        productList.clear();
        productList.addAll(filteredList);
        adapter.notifyDataSetChanged();
    }

    @Nullable
    private MainActivity mainActivity() {
        if (getActivity() instanceof MainActivity) {
            return (MainActivity) getActivity();
        }
        return null;
    }

    private void setupSwipeReveal() {
        try {
            InventorySwipeCallback callback = new InventorySwipeCallback(adapter);
            ItemTouchHelper helper = new ItemTouchHelper(callback);
            helper.attachToRecyclerView(rvProducts);
        } catch (Exception e) {

        }
    }

    public void onSwipeDeleteToCart(@NonNull Product product, int adapterPosition) {
        product.setDeleted(true);
        new Thread(() -> {
            db.productDao().update(product);
            postIfAlive(() -> {
                MainActivity act = mainActivity();
                if (act != null) act.syncProductToFirebase(product);


                fullList.remove(product);
                if (adapterPosition >= 0 && adapterPosition < productList.size()) {
                    productList.remove(adapterPosition);
                    adapter.notifyItemRemoved(adapterPosition);
                } else {
                    reloadFromDb();
                }
                Toast.makeText(requireContext(), "Moved to cart", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    public void reloadFromDb() {
        new Thread(() -> {
            List<Product> list = db.productDao().getAllActive();
            postIfAlive(() -> {
                fullList.clear();
                fullList.addAll(list);

                productList.clear();
                productList.addAll(list);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void postIfAlive(@NonNull Runnable action) {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) action.run();
        });
    }
}