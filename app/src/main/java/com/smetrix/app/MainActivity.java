package com.smetrix.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.WorkManager;

import com.smetrix.app.databinding.ActivityMainBinding;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.network.ApiClient;
import com.smetrix.app.network.AuthInterceptor;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.repository.AuthRepository;
import com.smetrix.app.ui.auth.AuthActivity;
import com.smetrix.app.ui.auth.ProfileFragment;
import com.smetrix.app.ui.common.SyncStatusView;
import com.smetrix.app.ui.conflict.ConflictResolutionActivity;
import com.smetrix.app.ui.project.ProjectListFragment;
import com.smetrix.app.ui.room.WorkerListFragment;
import com.smetrix.app.viewmodel.SyncViewModel;

import java.util.List;




















public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";


    private ActivityMainBinding binding;


    private SyncViewModel syncViewModel;


    private SyncStatusView syncStatusView;

    private final BroadcastReceiver logoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AuthInterceptor.ACTION_LOGOUT.equals(intent.getAction())) {
                return;
            }
            new AuthRepository(
                    getApplicationContext(),
                    ApiClient.getInstance(getApplicationContext()).getService()
            ).clearSession();

            Intent authIntent = new Intent(MainActivity.this, AuthActivity.class);
            authIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(authIntent);
            finish();
        }
    };





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthRepository authRepository = new AuthRepository(
                getApplicationContext(), ApiClient.getService(getApplicationContext()));
        if (!authRepository.hasActiveSession()) {
            Intent authIntent = new Intent(this, AuthActivity.class);
            authIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(authIntent);
            finish();
            return;
        }


        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());




        setSupportActionBar(binding.toolbar);




        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(backStackCount > 0);
                }
            }
        });





        if (savedInstanceState == null) {
            showFragment(new ProjectListFragment(), ProjectListFragment.TAG, false);
        }


        initSyncStatusView();

        if (authRepository.isLoggedIn()) {
            new SyncManager(WorkManager.getInstance(this)).scheduleSync();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                logoutReceiver,
                new IntentFilter(AuthInterceptor.ACTION_LOGOUT)
        );
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logoutReceiver);
        super.onStop();
    }
















    private void initSyncStatusView() {

        syncViewModel = new ViewModelProvider(this).get(SyncViewModel.class);


        syncStatusView = new SyncStatusView(this);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        binding.toolbar.addView(syncStatusView, lp);


        syncStatusView.bind(syncViewModel, this);

        AuthRepository authRepository = new AuthRepository(
                getApplicationContext(), ApiClient.getService(getApplicationContext()));
        if (authRepository.isGuest()) {
            syncStatusView.setVisibility(android.view.View.GONE);
        }


        syncStatusView.setOnClickListener(v -> {
            List<ConflictEntity> conflicts = syncViewModel.getConflicts().getValue();
            if (conflicts != null && !conflicts.isEmpty()) {
                ConflictEntity first = conflicts.get(0);
                Log.d(TAG, "SyncStatusView clicked: открываем ConflictResolutionActivity,"
                        + " entityId=" + first.entityId);
                Intent intent = new Intent(this, ConflictResolutionActivity.class);
                intent.putExtra(ConflictResolutionActivity.EXTRA_ENTITY_ID, first.entityId);
                startActivity(intent);
            } else {
                Log.d(TAG, "SyncStatusView clicked: конфликтов нет.");
            }
        });
    }








    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }











    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }

        if (id == R.id.action_profile) {
            showFragment(ProfileFragment.newInstance(), ProfileFragment.TAG, true);
            return true;
        }

        if (id == R.id.action_workers) {

            showFragment(new WorkerListFragment(), WorkerListFragment.TAG, true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
















    public void showFragment(Fragment fragment, String tag, boolean addToBackStack) {
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()

                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .replace(R.id.fragment_container, fragment, tag);

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }

        transaction.commit();
    }
}
