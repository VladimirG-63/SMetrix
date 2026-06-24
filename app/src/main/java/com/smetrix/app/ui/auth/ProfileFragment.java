package com.smetrix.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentProfileBinding;
import com.smetrix.app.viewmodel.ProfileViewModel;

public class ProfileFragment extends Fragment {

    public static final String TAG = "ProfileFragment";

    private FragmentProfileBinding binding;
    private ProfileViewModel profileViewModel;

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        setupObservers();
        setupListeners();

        if (profileViewModel.isGuest()) {
            binding.etProfileName.setEnabled(false);
            binding.etProfileEmail.setEnabled(false);
            binding.btnSaveProfile.setVisibility(View.GONE);
            binding.btnLogout.setText(R.string.auth_btn_login);
        }

        profileViewModel.loadProfile();
    }

    private void setupObservers() {
        profileViewModel.loading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnSaveProfile.setEnabled(!isLoading);
        });

        profileViewModel.profileName.observe(getViewLifecycleOwner(), name -> {
            if (name != null && !name.equals(
                    binding.etProfileName.getText() != null
                            ? binding.etProfileName.getText().toString() : "")) {
                binding.etProfileName.setText(name);
            }
        });

        profileViewModel.profileEmail.observe(getViewLifecycleOwner(), email -> {
            if (email != null && !email.equals(
                    binding.etProfileEmail.getText() != null
                            ? binding.etProfileEmail.getText().toString() : "")) {
                binding.etProfileEmail.setText(email);
            }
        });

        profileViewModel.updateSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                profileViewModel.onUpdateHandled();
                Toast.makeText(requireContext(),
                        "Данные успешно сохранены", Toast.LENGTH_SHORT).show();
            }
        });

        profileViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                profileViewModel.onErrorShown();
            }
        });
    }

    private void setupListeners() {
        binding.btnSaveProfile.setOnClickListener(v -> {
            String name = binding.etProfileName.getText() != null
                    ? binding.etProfileName.getText().toString().trim() : "";
            String email = binding.etProfileEmail.getText() != null
                    ? binding.etProfileEmail.getText().toString().trim() : "";
            profileViewModel.updateProfile(name, email);
        });

        binding.btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_logout_title)
                .setMessage(R.string.dialog_logout_message)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.dialog_logout_confirm, (dialog, which) -> {
                    profileViewModel.logout();
                    navigateToAuth();
                })
                .show();
    }

    private void navigateToAuth() {
        Intent intent = new Intent(requireActivity(), com.smetrix.app.ui.auth.AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
