
package com.smetrix.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.smetrix.app.ui.auth.AuthActivity;
import com.smetrix.app.viewmodel.AuthViewModel;














public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        Intent intent;
        if (authViewModel.hasActiveSession()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, AuthActivity.class);
        }


        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();



    }
}
