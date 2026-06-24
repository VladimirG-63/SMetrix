// app/src/main/java/com/smetrix/app/ui/auth/RegisterFragment.java
package com.smetrix.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.smetrix.app.MainActivity;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentRegisterBinding;
import com.smetrix.app.viewmodel.AuthViewModel;

/**
 * Экран регистрации нового пользователя.
 *
 * <p>Размещается внутри {@link AuthActivity}.
 * Открывается из {@link LoginFragment} через back stack.
 */
public class RegisterFragment extends Fragment {

    public static final String TAG = "RegisterFragment";

    private FragmentRegisterBinding binding;
    private AuthViewModel           authViewModel;

    // ─── Фабрика ─────────────────────────────────────────────────────────────

    public static RegisterFragment newInstance() {
        return new RegisterFragment();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authViewModel = new ViewModelProvider(requireActivity())
                .get(AuthViewModel.class);

        setupListeners();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─── Подписки ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        binding.btnRegister.setOnClickListener(v -> {
            String name            = getText(binding.etName);
            String email           = getText(binding.etEmail);
            String password        = getText(binding.etPassword);
            String confirmPassword = getText(binding.etConfirmPassword);
            authViewModel.register(name, email, password, confirmPassword);
        });

        binding.tvLogin.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
    }

    private void observeViewModel() {
        authViewModel.loading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnRegister.setEnabled(!isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        authViewModel.registerSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                authViewModel.onRegisterNavigated();
                navigateToMain();
            }
        });

        authViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
                authViewModel.onErrorShown();
            }
        });
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    private void navigateToMain() {
        Intent intent = new Intent(requireActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // ─── Вспомогательные ─────────────────────────────────────────────────────

    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }
}
