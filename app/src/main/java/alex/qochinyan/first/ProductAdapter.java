package alex.qochinyan.first;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final List<Product> productList;
    private final OnProductClickListener clickListener;
    private final OnProductEditListener editListener;
    private OnProductDeleteListener deleteListener;
    private int swipedPosition = -1;

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public interface OnProductEditListener {
        void onProductEdit(Product product);
    }

    public interface OnProductDeleteListener {
        void onProductDelete(Product product, int position);
    }

    public void setOnDeleteListener(OnProductDeleteListener listener) {
        this.deleteListener = listener;
    }

    public ProductAdapter(List<Product> productList,
                          OnProductClickListener clickListener,
                          @Nullable OnProductEditListener editListener) {
        this.productList = productList;
        this.clickListener = clickListener;
        this.editListener = editListener;
    }

    public void setSwipedPosition(int pos) {
        int previousSwipedPosition = swipedPosition;
        swipedPosition = pos;
        if (previousSwipedPosition != -1 && previousSwipedPosition < productList.size()) {
            notifyItemChanged(previousSwipedPosition);
        }
        if (swipedPosition != -1 && swipedPosition < productList.size()) {
            notifyItemChanged(swipedPosition);
        }
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


        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        float stickyTranslation = 200 * density;
        
        if (position == swipedPosition) {
            holder.cardView.setTranslationX(stickyTranslation);
        } else {
            holder.cardView.setTranslationX(0);
        }

        holder.tvName.setText(product.getName());
        holder.tvQuantity.setText("×" + Product.quantityForSave(product.getQuantity()));

        int statusColor = product.getStatusColor();
        holder.cardView.setStrokeColor(statusColor);
        holder.cardView.setStrokeWidth(6);
        holder.tvNotification.setTextColor(statusColor);

        if (Product.isPendingScan(product.getExpiryDate())) {
            holder.tvNotification.setText("Tap to set notification date & time");
        } else {
            String line = product.getNotificationDate();
            if (line == null || line.isEmpty()) {
                line = product.getExpiryDate() != null ? product.getExpiryDate() : "—";
            }
            holder.tvNotification.setText("Notify: " + line);
        }


        holder.btnEdit.setOnClickListener(v -> {
            if (editListener != null) editListener.onProductEdit(product);
            setSwipedPosition(-1); // Close after action
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onProductDelete(product, holder.getAdapterPosition());
            }
            setSwipedPosition(-1);
        });


        holder.cardView.setOnClickListener(v -> {
            if (swipedPosition == position) {
                setSwipedPosition(-1);
            } else if (swipedPosition != -1) {
                setSwipedPosition(-1);
            } else {
                if (clickListener != null) clickListener.onProductClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName, tvQuantity, tvNotification;
        public MaterialCardView cardView;
        public View btnEdit, btnDelete;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvNotification = itemView.findViewById(R.id.tvNotification);
            cardView = itemView.findViewById(R.id.productCard);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
