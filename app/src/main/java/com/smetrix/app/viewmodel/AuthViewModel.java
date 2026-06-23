
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smetrix.app.network.ApiClient;
import com.smetrix.app.network.ApiService;
import com.smetrix.app.network.NetworkUtils;
import com.smetrix.app.network.dto.ForgotPasswordRequest;
import com.smetrix.app.network.dto.LoginRequest;
import com.smetrix.app.network.dto.RegisterRequest;
import com.smetrix.app.viewmodel.ProfileViewModel;
import com.smetrix.app.network.dto.RegisterResponse;
import com.smetrix.app.network.dto.TokenResponse;
import com.smetrix.app.repository.AuthRepository;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;











public class AuthViewModel extends AndroidViewModel {

    private static final String TAG = "AuthViewModel";




    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public final LiveData<Boolean> loading = _loading;





    private final MutableLiveData<Boolean> _loginSuccess = new MutableLiveData<>(false);
    public final LiveData<Boolean> loginSuccess = _loginSuccess;





    private final MutableLiveData<Boolean> _registerSuccess = new MutableLiveData<>(false);
    public final LiveData<Boolean> registerSuccess = _registerSuccess;





    private final MutableLiveData<Boolean> _forgotPasswordSuccess = new MutableLiveData<>(false);
    public final LiveData<Boolean> forgotPasswordSuccess = _forgotPasswordSuccess;





    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>(null);
    public final LiveData<String> errorMessage = _errorMessage;



    private final AuthRepository authRepository;
    private final ApiService     apiService;



    public AuthViewModel(@NonNull Application application) {
        super(application);
        apiService     = ApiClient.getInstance(application).getService();
        authRepository = new AuthRepository(application, apiService);
    }








    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    public boolean hasActiveSession() {
        return authRepository.hasActiveSession();
    }

    public void enterGuestMode() {
        authRepository.enterGuestMode();
    }










    public void login(String email, String password) {
        if (!validateEmail(email) || !validatePassword(password)) {
            return;
        }

        _loading.setValue(true);
        _errorMessage.setValue(null);

        LoginRequest request = new LoginRequest(email.trim(), password);

        apiService.login(request).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(@NonNull Call<TokenResponse> call, @NonNull Response<TokenResponse> response) {
                _loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    TokenResponse body = response.body();
                    authRepository.saveTokens(body);
                    Log.d(TAG, "Вход выполнен успешно.");
                    ProfileViewModel.cacheUserData(
                            getApplication(),
                            body.name,
                            body.email != null ? body.email : email.trim());
                    _loginSuccess.setValue(true);
                } else {
                    try {
                        if (response.errorBody() != null) {
                            String serverError = response.errorBody().string();
                            _errorMessage.setValue(serverError);
                        } else {
                            _errorMessage.setValue("Неверный email или пароль.");
                        }
                    } catch (Exception e) {
                        _errorMessage.setValue("Ошибка обработки ответа");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<TokenResponse> call, @NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "login: сетевая ошибка", t);
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












    public void register(String name, String email, String password, String confirmPassword) {
        if (name == null || name.trim().isEmpty()) {
            _errorMessage.setValue("Введите имя.");
            return;
        }
        if (!validateEmail(email)) {
            return;
        }
        if (!validatePassword(password)) {
            return;
        }
        if (!password.equals(confirmPassword)) {
            _errorMessage.setValue("Пароли не совпадают.");
            return;
        }

        _loading.setValue(true);
        _errorMessage.setValue(null);

        RegisterRequest request = new RegisterRequest(name.trim(), email.trim(), password);

        apiService.register(request).enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(@NonNull Call<RegisterResponse> call,
                                   @NonNull Response<RegisterResponse> response) {
                _loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse body = response.body();

                    com.smetrix.app.network.dto.TokenResponse tokenResponse =
                            new com.smetrix.app.network.dto.TokenResponse();
                    tokenResponse.accessToken        = body.accessToken;
                    tokenResponse.refreshToken       = body.refreshToken;
                    tokenResponse.accessTokenExpiresIn = body.accessTokenExpiresIn;
                    authRepository.saveTokens(tokenResponse);
                    if (body.userId != null) {
                        authRepository.saveUserId(body.userId);
                    }
                    ProfileViewModel.cacheUserData(
                            getApplication(),
                            body.name != null ? body.name : name.trim(),
                            body.email != null ? body.email : email.trim());
                    Log.d(TAG, "Регистрация успешна.");
                    _registerSuccess.setValue(true);
                } else {
                    int code = response.code();
                    Log.w(TAG, "register: ошибка сервера code=" + code);
                    if (code == 409) {
                        _errorMessage.setValue("Пользователь с таким email уже существует.");
                    } else {
                        _errorMessage.setValue("Ошибка регистрации: " + code + ". Попробуйте позже.");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<RegisterResponse> call, @NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "register: сетевая ошибка", t);
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









    public void forgotPassword(String email) {
        if (!validateEmail(email)) {
            return;
        }

        _loading.setValue(true);
        _errorMessage.setValue(null);

        ForgotPasswordRequest request = new ForgotPasswordRequest(email.trim());

        apiService.forgotPassword(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call,
                                   @NonNull Response<Void> response) {
                _loading.setValue(false);

                if (response.isSuccessful()) {
                    Log.d(TAG, "Письмо для сброса пароля отправлено.");
                    _forgotPasswordSuccess.setValue(true);
                } else {
                    int code = response.code();
                    Log.w(TAG, "forgotPassword: ошибка сервера code=" + code);
                    if (code == 404) {
                        _errorMessage.setValue("Email не найден в системе.");
                    } else {
                        _errorMessage.setValue("Ошибка: " + code + ". Попробуйте позже.");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "forgotPassword: сетевая ошибка", t);
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


    public void onLoginNavigated() {
        _loginSuccess.setValue(false);
    }


    public void onRegisterNavigated() {
        _registerSuccess.setValue(false);
    }


    public void onForgotPasswordHandled() {
        _forgotPasswordSuccess.setValue(false);
    }


    public void onErrorShown() {
        _errorMessage.setValue(null);
    }



    private boolean validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            _errorMessage.setValue("Введите email.");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _errorMessage.setValue("Некорректный формат email.");
            return false;
        }
        return true;
    }

    private boolean validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            _errorMessage.setValue("Введите пароль.");
            return false;
        }
        if (password.length() < 6) {
            _errorMessage.setValue("Пароль должен содержать не менее 6 символов.");
            return false;
        }
        return true;
    }
    private final MutableLiveData<Boolean> _resetPasswordSuccess = new MutableLiveData<>(false);
    public final LiveData<Boolean> resetPasswordSuccess = _resetPasswordSuccess;


    public void onResetPasswordHandled() {
        _resetPasswordSuccess.setValue(false);
    }

    public void confirmPasswordReset(String email, String code, String newPassword) {
        _loading.setValue(true);
        _errorMessage.setValue(null);

        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("email", email);
        request.put("code", code);
        request.put("newPassword", newPassword);

        apiService.resetPassword(request).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(@androidx.annotation.NonNull Call<Void> call, @androidx.annotation.NonNull retrofit2.Response<Void> response) {
                _loading.setValue(false);
                if (response.isSuccessful()) {
                    _resetPasswordSuccess.setValue(true);
                } else {
                    _errorMessage.setValue("Неверный код или ошибка сервера");
                }
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull Call<Void> call, @androidx.annotation.NonNull Throwable t) {
                _loading.setValue(false);
                Log.e(TAG, "confirmPasswordReset: сетевая ошибка", t);
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
}
