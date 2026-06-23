
package com.smetrix.app.repository;

import androidx.lifecycle.LiveData;

import com.smetrix.app.db.dao.ProjectDao;
import com.smetrix.app.db.dao.ProjectRoomDao;
import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.dao.WorkTaskDao;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.model.SyncState;
import com.smetrix.app.model.UuidGenerator;
import com.smetrix.app.network.sync.SyncManager;

import java.math.BigDecimal;
import java.util.List;

































public class ProjectRepository {






    private final ProjectDao projectDao;
    private final ProjectRoomDao projectRoomDao;
    private final EstimateItemDao estimateItemDao;
    private final WorkTaskDao workTaskDao;





    private final SyncManager syncManager;

















    public ProjectRepository(ProjectDao projectDao,
                             ProjectRoomDao projectRoomDao,
                             EstimateItemDao estimateItemDao,
                             WorkTaskDao workTaskDao,
                             SyncManager syncManager) {
        this.projectDao = projectDao;
        this.projectRoomDao = projectRoomDao;
        this.estimateItemDao = estimateItemDao;
        this.workTaskDao = workTaskDao;
        this.syncManager = syncManager;
    }

























    public LiveData<List<ProjectEntity>> getActiveProjects(String userId) {


        return projectDao.getActiveProjects(userId);
    }

































    public void createProject(
            final String userId,
            final String name,
            final String city,
            final String regionCode,
            final BigDecimal taxMultiplier,
            final BigDecimal logisticsMarkup
    ) {



        final long currentTimeMillis = System.currentTimeMillis();


        final String newProjectId = UuidGenerator.generate();



        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {




                ProjectEntity newProject = new ProjectEntity();


                newProject.id = newProjectId;


                newProject.userId = userId;


                newProject.name = name;
                newProject.city = city;
                newProject.regionCode = regionCode;
                newProject.taxMultiplier = taxMultiplier;
                newProject.logisticsMarkup = logisticsMarkup;



                newProject.createdAt = currentTimeMillis;
                newProject.updatedAt = currentTimeMillis;




                newProject.version = 0L;



                newProject.syncState = SyncState.PENDING_CREATE.name();


                newProject.deletedAt = null;


                newProject.lastSyncedAt = 0L;




                projectDao.insert(newProject);





                syncManager.scheduleSync();
            }
        });
    }






























    public void softDelete(final String id) {

        final long deletedAtTimestamp = System.currentTimeMillis();


        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {

                projectDao.markDeleted(id, deletedAtTimestamp);


                projectRoomDao.markDeletedByProject(id, deletedAtTimestamp, SyncState.PENDING_DELETE);
                estimateItemDao.markDeletedByProject(id, deletedAtTimestamp, SyncState.PENDING_DELETE);
                workTaskDao.markDeletedByProject(id, deletedAtTimestamp, SyncState.PENDING_DELETE);


                syncManager.scheduleSync();
            }
        });
    }












    public void updateProject(
            final String id,
            final String name,
            final String city,
            final String region,
            final BigDecimal taxMultiplier,
            final BigDecimal logisticsMarkup
    ) {
        final long ts = System.currentTimeMillis();
        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                projectDao.updateProject(id, name, city, region,
                        taxMultiplier, logisticsMarkup, ts);
                syncManager.scheduleSync();
            }
        });
    }
}
