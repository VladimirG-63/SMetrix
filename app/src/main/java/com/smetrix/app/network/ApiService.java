
package com.smetrix.app.network;

import com.smetrix.app.network.dto.ForgotPasswordRequest;
import com.smetrix.app.network.dto.LoginRequest;
import com.smetrix.app.network.dto.MaterialSearchResponse;
import com.smetrix.app.network.dto.OpeningDto;
import com.smetrix.app.network.dto.ProjectDto;
import com.smetrix.app.network.dto.RegisterRequest;
import com.smetrix.app.network.dto.RegisterResponse;
import com.smetrix.app.network.dto.SyncBatchRequest;
import com.smetrix.app.network.dto.SyncBatchResponse;
import com.smetrix.app.network.dto.SyncPullResponse;
import com.smetrix.app.network.dto.TokenResponse;
import com.smetrix.app.network.dto.WorkerDto;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;











public interface ApiService {











    @POST("projects")
    Call<ProjectDto> createProject(@Body ProjectDto dto);












    @DELETE("projects/{id}")
    Call<Void> deleteProject(@Path("id") String id, @Body Map<String, Long> body);















    @POST("estimate-items/sync")
    Call<SyncBatchResponse> syncEstimateItems(@Body SyncBatchRequest request);







    @POST("project-rooms/sync")
    Call<SyncBatchResponse> syncProjectRooms(@Body SyncBatchRequest request);

    @POST("work-tasks/sync")
    Call<SyncBatchResponse> syncWorkTasks(@Body SyncBatchRequest request);

    @POST("openings/sync")
    Call<SyncBatchResponse> syncOpenings(@Body SyncBatchRequest request);

    @POST("workers/sync")
    Call<SyncBatchResponse> syncWorkers(@Body SyncBatchRequest request);

    @GET("sync/pull")
    Call<SyncPullResponse> pullChanges(@Query("since") long since);

















    @GET("materials")
    Call<MaterialSearchResponse> searchMaterials(
            @Query("q") String query,
            @Query("region") String regionCode,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @POST("materials/{code}/use")
    Call<Void> recordMaterialUse(@Path("code") String code, @Query("region") String regionCode);



















    @POST("auth/refresh")
    Call<TokenResponse> refreshToken(@Body Map<String, String> body);







    @POST("auth/login")
    Call<TokenResponse> login(@Body LoginRequest body);







    @POST("auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest body);







    @POST("auth/forgot-password")
    Call<Void> forgotPassword(@Body ForgotPasswordRequest body);




    @POST("auth/reset-password")
    Call<Void> resetPassword(@Body Map<String, String> request);










    @GET("user/profile")
    Call<Map<String, String>> getProfile();







    @PUT("user/profile")
    Call<Void> updateProfile(@Body com.smetrix.app.network.dto.UserProfileRequest body);
}
