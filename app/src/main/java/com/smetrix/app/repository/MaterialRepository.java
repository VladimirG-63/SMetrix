
package com.smetrix.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.annotation.NonNull;

import com.smetrix.app.db.dao.MaterialsCacheDao;
import com.smetrix.app.db.entity.MaterialsCacheEntity;
import com.smetrix.app.network.ApiService;
import com.smetrix.app.network.dto.MaterialDto;
import com.smetrix.app.network.dto.MaterialSearchResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MaterialRepository {

    private static final String TAG = "MaterialRepository";


    private final MaterialsCacheDao materialsCacheDao;


    private final ApiService apiService;


    public MaterialRepository(MaterialsCacheDao materialsCacheDao) {
        this.materialsCacheDao = materialsCacheDao;
        this.apiService = null;
    }


    public MaterialRepository(MaterialsCacheDao materialsCacheDao, ApiService apiService) {
        this.materialsCacheDao = materialsCacheDao;
        this.apiService = apiService;
    }


    public LiveData<List<MaterialsCacheEntity>> searchLocal(String query, String regionCode) {

        return materialsCacheDao.searchLocal(query, regionCode);
    }


    public void cacheFromServer(final List<MaterialsCacheEntity> serverItems) {

        if (serverItems == null || serverItems.isEmpty()) {

            return;
        }


        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {


                for (int i = 0; i < serverItems.size(); i++) {
                    MaterialsCacheEntity item = serverItems.get(i);


                    if (item == null) {

                        continue;
                    }


                    try {
                        Integer existingPriority = materialsCacheDao.getPriorityScore(
                                item.fgisCode, item.regionCode);
                        if (existingPriority != null) {
                            item.priorityScore = Math.max(item.priorityScore, existingPriority);
                        }


                        materialsCacheDao.insertOrReplace(item);
                    } catch (RuntimeException dbException) {
                        Log.e(TAG, "Ошибка кэширования материала; обработка списка продолжена.",
                                dbException);

                    }
                }
            }
        });
    }

    public void markMaterialUsed(@NonNull String fgisCode, @NonNull String regionCode) {
        AppExecutors.diskIO().execute(() ->
                materialsCacheDao.markUsed(fgisCode, regionCode, System.currentTimeMillis()));
        if (apiService != null && !fgisCode.startsWith("MANUAL-")) {
            apiService.recordMaterialUse(fgisCode, regionCode).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                }

                @Override
                public void onFailure(Call<Void> call, Throwable error) {
                    Log.d(TAG, "Не удалось обновить региональную популярность материала", error);
                }
            });
        }
    }

    public void cacheManual(@NonNull MaterialDto dto, @NonNull String regionCode) {
        MaterialsCacheEntity entity = new MaterialsCacheEntity();
        entity.fgisCode = dto.fgisCode;
        entity.name = dto.name;
        entity.unitMeasure = dto.unitMeasure;
        entity.regionCode = regionCode;
        entity.basePrice = new BigDecimal(dto.basePrice);
        entity.source = "MANUAL";
        entity.cachedAt = System.currentTimeMillis();
        entity.priorityScore = 1;
        cacheFromServer(java.util.Collections.singletonList(entity));
    }


    public void searchRemote(String query, String regionCode,
                             Callback<MaterialSearchResponse> callback) {
        if (apiService == null) {
            Log.w(TAG, "searchRemote: ApiService не инициализирован. "
                    + "Используйте конструктор MaterialRepository(dao, apiService).");
            return;
        }

        apiService.searchMaterials(query, regionCode, 20, 0)
                .enqueue(new Callback<MaterialSearchResponse>() {

                    @Override
                    public void onResponse(Call<MaterialSearchResponse> call,
                                           Response<MaterialSearchResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().items != null) {

                            List<MaterialDto> dtos = response.body().items;
                            List<MaterialsCacheEntity> entities = mapDtosToEntities(dtos, regionCode);
                            cacheFromServer(entities);
                            Log.d(TAG, "searchRemote: получено " + dtos.size()
                                    + " материалов с сервера, закэшировано в Room.");
                        } else {
                            Log.w(TAG, "searchRemote: сервер вернул пустой ответ или ошибку, code="
                                    + response.code());
                        }

                        callback.onResponse(call, response);
                    }

                    @Override
                    public void onFailure(Call<MaterialSearchResponse> call, Throwable throwable) {

                        Log.w(TAG, "searchRemote: сетевая ошибка при поиске материалов.",
                                throwable);

                        callback.onFailure(call, throwable);
                    }
                });
    }


    private List<MaterialsCacheEntity> mapDtosToEntities(List<MaterialDto> dtos,
                                                         String requestedRegionCode) {
        List<MaterialsCacheEntity> entities = new ArrayList<>(dtos.size());
        long now = System.currentTimeMillis();

        for (MaterialDto dto : dtos) {
            if (dto == null || dto.fgisCode == null) {
                continue;
            }
            MaterialsCacheEntity entity = new MaterialsCacheEntity();
            entity.fgisCode = dto.fgisCode;
            entity.name = dto.name;
            entity.unitMeasure = dto.unitMeasure;
            entity.regionCode = dto.regionCode != null && !dto.regionCode.trim().isEmpty()
                    ? dto.regionCode.trim() : requestedRegionCode;
            entity.basePrice = (dto.basePrice != null)
                    ? new BigDecimal(dto.basePrice) : BigDecimal.ZERO;
            entity.consumptionRate = (dto.consumptionRate != null)
                    ? new BigDecimal(dto.consumptionRate) : null;
            entity.source = "SERVER";
            entity.cachedAt = now;
            entity.priorityScore = dto.priorityScore;
            entities.add(entity);
        }
        return entities;
    }
}
