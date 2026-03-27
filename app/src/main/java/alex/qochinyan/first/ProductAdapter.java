package alex.qochinyan.first;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private List<Product> productList;
    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public ProductAdapter(List<Product> productList, OnProductClickListener listener) {
        this.productList = productList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.tvName.setText(product.getName());
        holder.tvDate.setText(product.getExpiryDate());

        // ОБЫЧНЫЙ КЛИК (проверяет, есть ли дата)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProductClick(product);
        });

        // ДЛИННЫЙ КЛИК (всегда открывает выбор даты/времени)
        holder.itemView.setOnLongClickListener(v -> {
            if (v.getContext() instanceof MainActivity) {
                ((MainActivity) v.getContext()).showDatePickerAndSetAlarm(product);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() { return productList.size(); }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate;
        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvDate = itemView.findViewById(R.id.tvExpiryDate);
        }
    }
}