// app/src/main/java/com/smetrix/app/ui/auth/ForgotPasswordFragment.java
package com.smetrix.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.smetrix.app.databinding.FragmentForgotPasswordBinding;
// ИСПРАВЛЕНО: удален неверный импорт ResetPasswordFragment
import com.smetrix.app.viewmodel.AuthViewModel;

public class ForgotPasswordFragment extends Fragment {

    public static final String TAG = "ForgotPasswordFragment";

    private FragmentForgotPasswordBinding binding;
    private AuthViewModel authViewModel;

    public static ForgotPasswordFragment newInstance() {
        return new ForgotPasswordFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        setupListeners();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupListeners() {
        binding.btnSendLink.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null
                    ? binding.etEmail.getText().toString().trim() : "";
            authViewModel.forgotPassword(email);
        });
        binding.tvBackToLogin.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
    }

    private void observeViewModel() {
        authViewModel.loading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnSendLink.setEnabled(!isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        authViewModel.forgotPasswordSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                authViewModel.onForgotPasswordHandled();
                Snackbar.make(binding.getRoot(),
                        "Код сброса отправлен на ваш email.",
                        Snackbar.LENGTH_LONG).show();

                String email = binding.etEmail.getText() != null
                        ? binding.etEmail.getText().toString().trim() : "";

                ResetPasswordFragment resetFragment = new ResetPasswordFragment();
                Bundle args = new Bundle();
                args.putString("email", email);
                resetFragment.setArguments(args);

                int containerId = ((ViewGroup) requireView().getParent()).getId();

                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(containerId, resetFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        authViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
                authViewModel.onErrorShown();
            }
        });
    }
}