
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.repository.ProjectRepository;
import com.smetrix.app.utils.SecurePrefsHelper;

import androidx.work.WorkManager;

import java.math.BigDecimal;
import java.util.List;







































public class ProjectListViewModel extends AndroidViewModel {






    private static final String TAG = "ProjectListViewModel";





    private static final String PREFS_KEY_USER_ID = "user_id";









    private final ProjectRepository projectRepository;






    private final LiveData<List<ProjectEntity>> projects;





    private final String userId;















    public ProjectListViewModel(@NonNull Application application) {
        super(application);


        SharedPreferences prefs = SecurePrefsHelper.get(application);
        String storedUserId = prefs.getString(PREFS_KEY_USER_ID, null);

        if (storedUserId == null || storedUserId.isEmpty()) {
            throw new IllegalStateException("ProjectListViewModel создан без активной сессии");
        }
        this.userId = storedUserId;


        AppDatabase database = AppDatabase.getInstance(application);


        WorkManager workManager = WorkManager.getInstance(application);
        SyncManager syncManager = new SyncManager(workManager);


        this.projectRepository = new ProjectRepository(
                database.projectDao(),
                database.projectRoomDao(),
                database.estimateItemDao(),
                database.workTaskDao(),
                syncManager
        );




        this.projects = projectRepository.getActiveProjects(this.userId);

        Log.d(TAG, "ProjectListViewModel инициализирован.");
    }


















    public LiveData<List<ProjectEntity>> getProjects() {
        return projects;
    }






















    public void createProject(
            final String name,
            final String city,
            final String regionCode,
            final BigDecimal taxMultiplier,
            final BigDecimal logisticsMarkup
    ) {
        Log.d(TAG, "createProject: создание проекта.");


        projectRepository.createProject(
                userId,
                name,
                city,
                regionCode,
                taxMultiplier,
                logisticsMarkup
        );
    }















    public void deleteProject(final String projectId) {
        Log.d(TAG, "deleteProject: projectId=" + projectId);
        projectRepository.softDelete(projectId);
    }












    public void updateProject(
            final String id,
            final String name,
            final String city,
            final String region,
            final BigDecimal taxMultiplier,
            final BigDecimal logisticsMarkup) {
        Log.d(TAG, "updateProject: обновление проекта.");
        projectRepository.updateProject(id, name, city, region,
                taxMultiplier, logisticsMarkup);
    }
}
