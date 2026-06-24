package com.smetrix.app.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smetrix.app.network.ApiClient;
import com.smetrix.app.network.ApiService;
import com.smetrix.app.network.NetworkUtils;
import com.smetrix.app.network.dto.UserProfileRequest;
import com.smetrix.app.repository.AuthRepository;
import com.smetrix.app.utils.SecurePrefsHelper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileViewModel extends AndroidViewModel {

    private static final String TAG = "ProfileViewModel";

    private static final String PREFS_NAME = "smetrix_auth_prefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";

    private final ApiService apiService;
    private final AuthRepository authRepository;
    private final SharedPreferences prefs;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public final LiveData<Boolean> loading = _loading;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>(null);
    public final LiveData<String> errorMessage = _errorMessage;

    private final MutableLiveData<Boolean> _updateSuccess = new MutableLiveData<>(false);
    public final LiveData<Boolean> updateSuccess = _updateSuccess;

    private final MutableLiveData<String> _profileName = new MutableLiveData<>("");
    public final LiveData<String> profileName = _profileName;

    private final MutableLiveData<String> _profileEmail = new MutableLiveData<>("");
    public final LiveData<String> profileEmail = _profileEmail;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        apiService = ApiClient.getInstance(application).getService();
        authRepository = new AuthRepository(application, apiService);
        prefs = SecurePrefsHelper.get(application);
    }

    /**
     * Загружает профиль пользователя.
     * Сначала подставляет кэшированные данные из SharedPreferences (мгновенно),
     * затем делает запрос к серверу для актуализации данных.
     */
    public void loadProfile() {
        if (authRepository.isGuest()) {
            _profileName.setValue("Гость");
            _profileEmail.setValue("");
            _loading.setValue(false);
            return;
        }
        String cachedName = prefs.getString(KEY_USER_NAME, "");
        String cachedEmail = prefs.getString(KEY_USER_EMAIL, "");
        if (cachedName != null && !cachedName.isEmpty()) {
            _profileName.setValue(cachedName);
        }
        if (cachedEmail != null && !cachedEmail.isEmpty()) {
            _profileEmail.setValue(cachedEmail);
        }

        _loading.setValue(true);
        apiService.getProfile().enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, String>> call,
                                   @NonNull Response<Map<String, String>> response) {
                _loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, String> body = response.body();
                    String name = body.getOrDefault("name", "");
                    String email = body.getOrDefault("email", "");
                    _profileName.setValue(name);
                    _profileEmail.setValue(email);
                    prefs.edit()
                            .putString(KEY_USER_NAME, name)
                            .putString(KEY_USER_EMAIL, email)
                            .apply();
                } else {
                    Log.w(TAG, "loadProfile: сервер вернул " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "loadProfile: ошибка сети", t);
                if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
                    _errorMessage.setValue("Не удалось подключиться к серверу. Убедитесь, что сервер запущен.");
                } else if (!NetworkUtils.isConnected(getApplication())) {
                    _errorMessage.setValue("Нет подключения к интернету. Проверьте соединение.");
                }
            }
        });
    }

    /**
     * Обновляет профиль. Если поля не изменены, передаёт текущие значения.
     * Если оба поля пусты — показывает ошибку.
     *
     * @param name  новое имя (может совпадать с текущим)
     * @param email новый email (может совпадать с текущим)
     */
    public void updateProfile(String name, String email) {
        if (authRepository.isGuest()) {
            _errorMessage.setValue("Профиль доступен после входа в аккаунт.");
            return;
        }
        String finalName = (name != null && !name.trim().isEmpty())
                ? name.trim()
                : prefs.getString(KEY_USER_NAME, "");
        String finalEmail = (email != null && !email.trim().isEmpty())
                ? email.trim()
                : prefs.getString(KEY_USER_EMAIL, "");

        if (finalName.isEmpty()) {
            _errorMessage.setValue("Введите имя.");
            return;
        }
        if (finalEmail.isEmpty()) {
            _errorMessage.setValue("Введите email.");
            return;
        }

        _loading.setValue(true);
        _errorMessage.setValue(null);

        UserProfileRequest request = new UserProfileRequest(finalName, finalEmail);

        apiService.updateProfile(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                _loading.setValue(false);
                if (response.isSuccessful()) {
                    _profileName.setValue(finalName);
                    _profileEmail.setValue(finalEmail);
                    prefs.edit()
                            .putString(KEY_USER_NAME, finalName)
                            .putString(KEY_USER_EMAIL, finalEmail)
                            .apply();
                    _updateSuccess.setValue(true);
                } else {
                    try {
                        String err = response.errorBody() != null
                                ? response.errorBody().string()
                                : "Ошибка сервера: " + response.code();
                        _errorMessage.setValue(err);
                    } catch (Exception e) {
                        _errorMessage.setValue("Ошибка обработки ответа: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "updateProfile: ошибка сети", t);
                if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
                    _errorMessage.setValue("Не удалось подключиться к серверу. Убедитесь, что сервер запущен.");
                } else if (!NetworkUtils.isConnected(getApplication())) {
                    _errorMessage.setValue("Нет подключения к интернету. Проверьте соединение.");
                } else {
                    _errorMessage.setValue("Сетевая ошибка: " + t.getMessage());
                }
            }
        });
    }

    public boolean isGuest() {
        return authRepository.isGuest();
    }

    /**
     * Сохраняет имя и email пользователя в SharedPreferences после успешного входа/регистрации.
     * Вызывается из AuthViewModel при успешном login/register.
     */
    public static void cacheUserData(Context context, String name, String email) {
        SecurePrefsHelper.get(context)
                .edit()
                .putString(KEY_USER_NAME, name != null ? name : "")
                .putString(KEY_USER_EMAIL, email != null ? email : "")
                .apply();
    }

    public void logout() {
        authRepository.clearSession();
    }

    public void onUpdateHandled() {
        _updateSuccess.setValue(false);
    }

    public void onErrorShown() {
        _errorMessage.setValue(null);
    }
}
