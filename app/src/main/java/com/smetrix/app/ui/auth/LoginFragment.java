
package com.smetrix.app.ui.auth;

import  android.content.Intent;
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
import com.smetrix.app.databinding.FragmentLoginBinding;
import com.smetrix.app.viewmodel.AuthViewModel;








public class LoginFragment extends Fragment {

    public static final String TAG = "LoginFragment";

    private FragmentLoginBinding binding;
    private AuthViewModel        authViewModel;



    public static LoginFragment newInstance() {
        return new LoginFragment();
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
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



    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email    = binding.etEmail.getText() != null
                    ? binding.etEmail.getText().toString() : "";
            String password = binding.etPassword.getText() != null
                    ? binding.etPassword.getText().toString() : "";
            authViewModel.login(email, password);
        });

        binding.tvRegister.setOnClickListener(v -> navigateTo(RegisterFragment.newInstance(),
                RegisterFragment.TAG));

        binding.tvForgotPassword.setOnClickListener(v -> navigateTo(
                ForgotPasswordFragment.newInstance(), ForgotPasswordFragment.TAG));

        binding.btnGuest.setOnClickListener(v -> {
            authViewModel.enterGuestMode();
            navigateToMain();
        });
    }

    private void observeViewModel() {
        authViewModel.loading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnLogin.setEnabled(!isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        authViewModel.loginSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                authViewModel.onLoginNavigated();
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



    private void navigateTo(Fragment fragment, String tag) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right,
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                )
                .replace(com.smetrix.app.R.id.auth_fragment_container, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    private void navigateToMain() {
        Intent intent = new Intent(requireActivity(), MainActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
