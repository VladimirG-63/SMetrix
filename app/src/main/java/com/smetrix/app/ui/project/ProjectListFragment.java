
package com.smetrix.app.ui.project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smetrix.app.MainActivity;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentProjectListBinding;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.ui.adapter.ProjectAdapter;
import com.smetrix.app.ui.room.RoomListFragment;
import com.smetrix.app.viewmodel.ProjectListViewModel;

import java.math.BigDecimal;
import java.util.List;

















public class ProjectListFragment extends Fragment {


    public static final String TAG = "ProjectListFragment";










    private FragmentProjectListBinding binding;





    private ProjectListViewModel viewModel;
    private ProjectAdapter adapter;





    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentProjectListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        viewModel = new ViewModelProvider(this).get(ProjectListViewModel.class);


        setupRecyclerView();
        setupFab();
        observeData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();


        binding = null;
    }









    private void setupRecyclerView() {

        adapter = new ProjectAdapter(new ProjectAdapter.OnProjectClickListener() {
            @Override
            public void onProjectClick(@NonNull ProjectEntity project) {

                navigateToRooms(project);
            }

            @Override
            public void onProjectLongClick(@NonNull ProjectEntity project) {

                showDeleteDialog(project);
            }

            @Override
            public void onProjectEditClick(@NonNull ProjectEntity project) {

                showEditProjectDialog(project);
            }
        });


        binding.rvProjects.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        binding.rvProjects.setAdapter(adapter);


        binding.rvProjects.setHasFixedSize(false);
    }




    private void setupFab() {
        binding.fabAddProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateProjectDialog();
            }
        });
    }










    private void observeData() {


        viewModel.getProjects().observe(getViewLifecycleOwner(), new Observer<List<ProjectEntity>>() {
            @Override
            public void onChanged(List<ProjectEntity> projects) {
                adapter.submitList(projects);

                updateEmptyState(projects);
            }
        });
    }









    private void updateEmptyState(@Nullable List<ProjectEntity> projects) {
        if (binding == null) {
            return;
        }

        boolean isEmpty = projects == null || projects.isEmpty();


        binding.tvEmptyProjects.setVisibility(isEmpty ? View.VISIBLE : View.GONE);



        binding.rvProjects.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }











    private void showCreateProjectDialog() {

        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_project, null);


        final TextInputLayout tilName   = formView.findViewById(R.id.tilProjectName);
        final TextInputLayout tilCity   = formView.findViewById(R.id.tilProjectCity);
        final TextInputLayout tilRegion = formView.findViewById(R.id.tilRegionCode);
        final TextInputLayout tilTax = formView.findViewById(R.id.tilTaxMultiplier);
        final TextInputLayout tilLogistics = formView.findViewById(R.id.tilLogisticsMarkup);


        final TextInputEditText etName   = formView.findViewById(R.id.etProjectName);
        final TextInputEditText etCity   = formView.findViewById(R.id.etProjectCity);
        final TextInputEditText etRegion = formView.findViewById(R.id.etRegionCode);
        final TextInputEditText etTax = formView.findViewById(R.id.etTaxMultiplier);
        final TextInputEditText etLogistics = formView.findViewById(R.id.etLogisticsMarkup);
        etTax.setText("1.00");
        etLogistics.setText("0");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_add_project)
                .setView(formView)
                .setPositiveButton(R.string.label_create, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {


            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name   = etName.getText()   != null ? etName.getText().toString().trim()   : "";
                String city   = etCity.getText()   != null ? etCity.getText().toString().trim()   : "";
                String region = etRegion.getText() != null ? etRegion.getText().toString().trim() : "";

                boolean hasError = false;


                if (name.isEmpty()) {
                    if (tilName != null) {
                        tilName.setError(getString(R.string.error_field_required));
                    } else {
                        etName.setError(getString(R.string.error_field_required));
                    }
                    hasError = true;
                } else if (tilName != null) {
                    tilName.setError(null);
                }


                if (city.isEmpty()) {
                    if (tilCity != null) {
                        tilCity.setError(getString(R.string.error_field_required));
                    } else {
                        etCity.setError(getString(R.string.error_field_required));
                    }
                    hasError = true;
                } else if (tilCity != null) {
                    tilCity.setError(null);
                }


                if (region.isEmpty()) {
                    if (tilRegion != null) {
                        tilRegion.setError(getString(R.string.error_field_required));
                    } else {
                        etRegion.setError(getString(R.string.error_field_required));
                    }
                    hasError = true;
                } else if (tilRegion != null) {
                    tilRegion.setError(null);
                }

                if (hasError) {
                    return;
                }

                BigDecimal defaultTax = parseProjectNumber(etTax, tilTax, BigDecimal.ZERO);
                BigDecimal defaultLogistics = parseProjectNumber(
                        etLogistics, tilLogistics, new BigDecimal("-100"));
                if (defaultTax == null || defaultLogistics == null) return;

                viewModel.createProject(name, city, region, defaultTax, defaultLogistics);
                dialog.dismiss();
            });
        });

        dialog.show();
    }






    private void showDeleteDialog(@NonNull ProjectEntity project) {
        String message = getString(R.string.dialog_delete_project_message, project.name);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_project_title)
                .setMessage(message)
                .setPositiveButton(R.string.label_delete, (dialogInterface, which) -> {
                    viewModel.deleteProject(project.id);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }






    private void showEditProjectDialog(@NonNull ProjectEntity project) {
        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_project, null);

        final TextInputLayout tilName   = formView.findViewById(R.id.tilProjectName);
        final TextInputLayout tilCity   = formView.findViewById(R.id.tilProjectCity);
        final TextInputLayout tilRegion = formView.findViewById(R.id.tilRegionCode);
        final TextInputLayout tilTax = formView.findViewById(R.id.tilTaxMultiplier);
        final TextInputLayout tilLogistics = formView.findViewById(R.id.tilLogisticsMarkup);

        final TextInputEditText etName   = formView.findViewById(R.id.etProjectName);
        final TextInputEditText etCity   = formView.findViewById(R.id.etProjectCity);
        final TextInputEditText etRegion = formView.findViewById(R.id.etRegionCode);
        final TextInputEditText etTax = formView.findViewById(R.id.etTaxMultiplier);
        final TextInputEditText etLogistics = formView.findViewById(R.id.etLogisticsMarkup);


        etName.setText(project.name);
        etCity.setText(project.city);
        etRegion.setText(project.regionCode);
        etTax.setText(project.taxMultiplier != null
                ? project.taxMultiplier.toPlainString() : "1.00");
        etLogistics.setText(project.logisticsMarkup != null
                ? project.logisticsMarkup.toPlainString() : "0");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_edit_project)
                .setView(formView)
                .setPositiveButton(R.string.label_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name   = etName.getText()   != null ? etName.getText().toString().trim()   : "";
                String city   = etCity.getText()   != null ? etCity.getText().toString().trim()   : "";
                String region = etRegion.getText() != null ? etRegion.getText().toString().trim() : "";

                boolean hasError = false;

                if (name.isEmpty()) {
                    if (tilName != null) tilName.setError(getString(R.string.error_field_required));
                    hasError = true;
                } else if (tilName != null) tilName.setError(null);

                if (city.isEmpty()) {
                    if (tilCity != null) tilCity.setError(getString(R.string.error_field_required));
                    hasError = true;
                } else if (tilCity != null) tilCity.setError(null);

                if (region.isEmpty()) {
                    if (tilRegion != null) tilRegion.setError(getString(R.string.error_field_required));
                    hasError = true;
                } else if (tilRegion != null) tilRegion.setError(null);

                if (hasError) return;

                BigDecimal tax = parseProjectNumber(etTax, tilTax, BigDecimal.ZERO);
                BigDecimal logistics = parseProjectNumber(
                        etLogistics, tilLogistics, new BigDecimal("-100"));
                if (tax == null || logistics == null) return;

                viewModel.updateProject(project.id, name, city, region, tax, logistics);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    @Nullable
    private BigDecimal parseProjectNumber(@NonNull TextInputEditText input,
                                          @NonNull TextInputLayout layout,
                                          @NonNull BigDecimal minimum) {
        try {
            String raw = input.getText() != null
                    ? input.getText().toString().trim().replace(',', '.') : "";
            BigDecimal value = new BigDecimal(raw);
            if (value.compareTo(minimum) < 0) throw new NumberFormatException();
            layout.setError(null);
            return value;
        } catch (NumberFormatException error) {
            layout.setError(getString(R.string.error_invalid_number));
            return null;
        }
    }










    private void navigateToRooms(@NonNull ProjectEntity project) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.showFragment(
                    RoomListFragment.newInstance(project.id),
                    RoomListFragment.TAG,
                    true
            );
        }
    }
}
