
package com.smetrix.app.network;

import android.content.Context;
import android.util.Log;

import com.smetrix.app.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
























public class ApiClient {

    private static final String TAG = "ApiClient";


    private static volatile ApiClient INSTANCE;

    private final ApiService apiService;






    private ApiClient(Context context) {
        Context appContext = context.getApplicationContext();

        OkHttpClient okHttpClient = buildOkHttpClient(appContext);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        Log.d(TAG, "ApiClient инициализирован с базовым URL: " + BuildConfig.API_BASE_URL);
    }










    public static ApiClient getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ApiClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ApiClient(context);
                }
            }
        }
        return INSTANCE;
    }






    public ApiService getService() {
        return apiService;
    }









    public static ApiService getService(Context context) {
        return getInstance(context).getService();
    }











    private OkHttpClient buildOkHttpClient(Context appContext) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();


        builder.addInterceptor(new AuthInterceptor(appContext));


        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                    message -> Log.d(TAG, message)
            );
            loggingInterceptor.redactHeader("Authorization");
            loggingInterceptor.redactHeader("Cookie");
            loggingInterceptor.redactHeader("Set-Cookie");

            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

            builder.addNetworkInterceptor(loggingInterceptor);
        }

        return builder.build();
    }
}
