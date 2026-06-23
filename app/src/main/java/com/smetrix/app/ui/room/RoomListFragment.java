
package com.smetrix.app.ui.room;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smetrix.app.MainActivity;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentRoomListBinding;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.viewmodel.RoomListViewModel;
import com.smetrix.app.viewmodel.RoomListViewModelFactory;

import java.util.List;










public class RoomListFragment extends Fragment {


    public static final String TAG = "RoomListFragment";


    private static final String ARG_PROJECT_ID = "project_id";





    private FragmentRoomListBinding binding;





    private RoomListViewModel viewModel;
    private RoomAdapter adapter;











    @NonNull
    public static RoomListFragment newInstance(@NonNull String projectId) {
        RoomListFragment fragment = new RoomListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }





    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRoomListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        Bundle args = getArguments();
        String projectId = (args != null) ? args.getString(ARG_PROJECT_ID) : null;
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalStateException(
                    "RoomListFragment требует аргумент ARG_PROJECT_ID. "
                    + "Используйте RoomListFragment.newInstance(projectId).");
        }


        viewModel = new ViewModelProvider(
                this,
                new RoomListViewModelFactory(requireActivity().getApplication(), projectId)
        ).get(RoomListViewModel.class);

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
        adapter = new RoomAdapter(new RoomAdapter.OnRoomActionListener() {
            @Override
            public void onClick(ProjectRoomEntity room) {

                navigateToRoomDetail(room.id);
            }

            @Override
            public void onLongClick(ProjectRoomEntity room) {
                showDeleteRoomDialog(room);
            }

            @Override
            public void onEditClick(ProjectRoomEntity room) {
                showEditRoomNameDialog(room);
            }
        });

        binding.rvRooms.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRooms.setAdapter(adapter);
        binding.rvRooms.setHasFixedSize(false);
    }

    private void setupFab() {
        binding.fabAddRoom.setOnClickListener(v -> showCreateRoomDialog());
    }





    private void observeData() {
        viewModel.getRooms().observe(getViewLifecycleOwner(), rooms -> {
            adapter.submitList(rooms);

            updateEmptyState(rooms);
        });
    }






    private void updateEmptyState(@Nullable List<ProjectRoomEntity> rooms) {
        boolean isEmpty = rooms == null || rooms.isEmpty();
        binding.tvEmptyRooms.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvRooms.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }










    private void navigateToRoomDetail(@NonNull String roomId) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.showFragment(
                    RoomDetailFragment.newInstance(roomId),
                    RoomDetailFragment.TAG,
                    true
            );
        }
    }








    private void showCreateRoomDialog() {
        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_room, null);

        final TextInputEditText etRoomName = formView.findViewById(R.id.etRoomName);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_add_room)
                .setView(formView)
                .setPositiveButton(R.string.label_create, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etRoomName.getText() != null
                            ? etRoomName.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        etRoomName.setError(getString(R.string.hint_room_name));
                        return;
                    }
                    viewModel.createRoom(name);
                    dialog.dismiss();
                }));

        dialog.show();
    }






    private void showDeleteRoomDialog(@NonNull ProjectRoomEntity room) {
        String message = getString(R.string.dialog_delete_room_message, room.name);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_room_title)
                .setMessage(message)
                .setPositiveButton(R.string.label_delete, (dialogInterface, which) -> {
                    viewModel.deleteRoom(room.id);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }






    private void showEditRoomNameDialog(@NonNull ProjectRoomEntity room) {
        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_room, null);

        final TextInputEditText etRoomName = formView.findViewById(R.id.etRoomName);

        etRoomName.setText(room.name);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_edit_room)
                .setView(formView)
                .setPositiveButton(R.string.label_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etRoomName.getText() != null
                            ? etRoomName.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        etRoomName.setError(getString(R.string.error_field_required));
                        return;
                    }
                    viewModel.updateRoomName(room.id, name);
                    dialog.dismiss();
                }));

        dialog.show();
    }
}
