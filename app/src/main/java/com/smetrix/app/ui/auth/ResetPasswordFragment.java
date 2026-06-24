package com.smetrix.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.smetrix.app.R;
import com.smetrix.app.viewmodel.AuthViewModel;

public class ResetPasswordFragment extends Fragment {

    private AuthViewModel authViewModel;
    private String userEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reset_password, container, false);

        EditText etCode = view.findViewById(R.id.etResetCode);
        EditText etNewPassword = view.findViewById(R.id.etNewPassword);
        Button btnSavePassword = view.findViewById(R.id.btnSavePassword);

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        if (getArguments() != null) {
            userEmail = getArguments().getString("email");
        }

        btnSavePassword.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            String password = etNewPassword.getText().toString().trim();

            if (code.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            authViewModel.confirmPasswordReset(userEmail, code, password);
        });

        // Слушаем ОТДЕЛЬНУЮ LiveData для сброса пароля (не forgotPasswordSuccess!)
        authViewModel.resetPasswordSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                authViewModel.onResetPasswordHandled();
                Toast.makeText(requireContext(), "Пароль успешно изменён! Войдите с новым паролем.", Toast.LENGTH_LONG).show();
                // Полностью очищаем backstack — возвращаемся на LoginFragment
                requireActivity().getSupportFragmentManager()
                        .popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        });

        authViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), String.valueOf(error), Toast.LENGTH_SHORT).show();
                authViewModel.onErrorShown();
            }
        });

        return view;
    }
}