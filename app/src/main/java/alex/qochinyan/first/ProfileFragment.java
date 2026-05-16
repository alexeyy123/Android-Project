package alex.qochinyan.first;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private TextView tvTotalCount;
    private TextView tvExpiringCount;


    private ProgressBar pbStorageLoad;
    private TextView tvStoragePercent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);
        TextView tvEmail = v.findViewById(R.id.tvUserEmail);
        MaterialButton btnLogout = v.findViewById(R.id.btnLogout);
        tvTotalCount = v.findViewById(R.id.tvTotalCount);
        tvExpiringCount = v.findViewById(R.id.tvExpiringCount);


        pbStorageLoad = v.findViewById(R.id.pbStorageLoad);
        tvStoragePercent = v.findViewById(R.id.tvStoragePercent);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();


        if (user == null) {
            tvEmail.setText("Guest Mode");
            btnLogout.setText("Log In / Sign Up");
            btnLogout.setOnClickListener(view -> {
                startActivity(new Intent(requireContext(), LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                requireActivity().finish();
            });
            return v;
        }

        String email = user.getEmail();
        tvEmail.setText(email != null && !email.isEmpty() ? email : "—");

        btnLogout.setOnClickListener(view -> {
            auth.signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            requireActivity().finish();
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void loadStatistics() {
        if (getContext() == null) return;
        AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
        new Thread(() -> {
            long now = System.currentTimeMillis();
            long weekLater = now + 7L * 24 * 60 * 60 * 1000;
            int total = db.productDao().countActiveInventory();
            int soon = db.productDao().countExpiringSoon(now, weekLater);


            int maxCapacity = 30;
            int percent = 0;
            if (maxCapacity > 0) {
                percent = (total * 100) / maxCapacity;
            }
            if (percent > 100) percent = 100;

            final int finalPercent = percent;

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        tvTotalCount.setText(String.valueOf(total));
                        tvExpiringCount.setText(String.valueOf(soon));


                        if (pbStorageLoad != null && tvStoragePercent != null) {
                            pbStorageLoad.setProgress(finalPercent);
                            tvStoragePercent.setText(finalPercent + "%");


                            if (finalPercent >= 85) {
                                pbStorageLoad.getProgressDrawable().setColorFilter(
                                        android.graphics.Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
                            } else {
                                pbStorageLoad.getProgressDrawable().clearColorFilter();
                            }
                        }
                    }
                });
            }
        }).start();
    }
}