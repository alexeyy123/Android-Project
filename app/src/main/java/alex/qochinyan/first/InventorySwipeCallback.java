package alex.qochinyan.first;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Sticky Swipe: Items stay open after a right swipe.
 * Clamped to 200dp to keep buttons visible and clickable.
 */
public class InventorySwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final ProductAdapter adapter;

    public InventorySwipeCallback(ProductAdapter adapter) {
        super(0, ItemTouchHelper.RIGHT);
        this.adapter = adapter;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Mark this position as swiped in the adapter to keep it open
        adapter.setSwipedPosition(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            ProductAdapter.ProductViewHolder holder = (ProductAdapter.ProductViewHolder) viewHolder;
            View cardView = holder.cardView;
            
            // Fixed 200dp limit for the sticky effect
            float density = recyclerView.getContext().getResources().getDisplayMetrics().density;
            float maxSwipe = 200 * density;
            
            // Clamp the visual translation during the swipe gesture
            float translationX = Math.min(dX, maxSwipe);
            cardView.setTranslationX(translationX);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.3f; // Swipe 30% of width to trigger the "swiped" state
    }
}
