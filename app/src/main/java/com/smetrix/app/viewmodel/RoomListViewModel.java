
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.repository.RoomRepository;

import androidx.work.WorkManager;

import java.util.List;







public class RoomListViewModel extends AndroidViewModel {

    private static final String TAG = "RoomListViewModel";

    private final RoomRepository roomRepository;
    private final LiveData<List<ProjectRoomEntity>> rooms;
    private final String projectId;

    public RoomListViewModel(@NonNull Application application, @NonNull String projectId) {
        super(application);
        this.projectId = projectId;

        AppDatabase database = AppDatabase.getInstance(application);
        WorkManager workManager = WorkManager.getInstance(application);
        SyncManager syncManager = new SyncManager(workManager);

        this.roomRepository = new RoomRepository(
                database.projectRoomDao(),
                database.estimateItemDao(),
                database.workTaskDao(),
                database.openingDao(),
                syncManager
        );

        this.rooms = database.projectRoomDao().getRoomsByProject(projectId);

        Log.d(TAG, "RoomListViewModel инициализирован для projectId=" + projectId);
    }






    public LiveData<List<ProjectRoomEntity>> getRooms() {
        return rooms;
    }






    public void createRoom(@NonNull String name) {
        Log.d(TAG, "createRoom: создание комнаты.");
        roomRepository.createRoom(projectId, name);
    }










    public void deleteRoom(@NonNull String roomId) {
        Log.d(TAG, "deleteRoom: roomId=" + roomId);
        roomRepository.deleteRoom(roomId);
    }










    public void updateRoomName(@NonNull String roomId, @NonNull String newName) {
        Log.d(TAG, "updateRoomName: обновление комнаты.");
        roomRepository.updateRoomName(roomId, newName);
    }
}
