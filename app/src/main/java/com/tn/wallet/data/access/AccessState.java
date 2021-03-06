package com.tn.wallet.data.access;

import android.content.Context;
import android.util.Log;

import com.tn.wallet.crypto.AESUtil;
import com.tn.wallet.data.auth.WavesWallet;
import com.tn.wallet.data.rxjava.RxUtil;
import com.tn.wallet.data.services.PinStoreService;
import com.tn.wallet.db.DBHelper;
import com.tn.wallet.ui.auth.EnvironmentManager;
import com.tn.wallet.util.AppUtil;
import com.tn.wallet.util.PrefsUtil;

import org.apache.commons.io.Charsets;
import org.spongycastle.util.encoders.Hex;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.Exceptions;
import io.realm.RealmConfiguration;

public class AccessState {

    private static final String TAG = AccessState.class.getSimpleName();

    private static final long CLEAR_TIMEOUT_SECS = 60L;

    private PrefsUtil prefs;
    private PinStoreService pinStore;
    private AppUtil appUtil;
    private static AccessState instance;
    private Disposable disposable;

    private WavesWallet wavesWallet;
    private boolean onDexScreens = false;

    public void initAccessState(Context context, PrefsUtil prefs, PinStoreService pinStore, AppUtil appUtil) {
        this.prefs = prefs;
        this.pinStore = pinStore;
        this.appUtil = appUtil;
    }

    public static AccessState getInstance() {
        if (instance == null)
            instance = new AccessState();
        return instance;
    }

    public Completable createPin(String walletGuid, String password, String passedPin) {
        return createPinObservable(walletGuid, password, passedPin)
                .compose(RxUtil.applySchedulersToCompletable());
    }

    public boolean restoreWavesWallet(String password) {
        String encryptedWallet = prefs.getValue(PrefsUtil.KEY_ENCRYPTED_WALLET, "");

        try {
            setTemporary(new WavesWallet(encryptedWallet, password));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setOnDexScreens(boolean onDexScreens) {
        this.onDexScreens = onDexScreens;
        if (!onDexScreens){
            setTemporary(wavesWallet);
        }
    }

    public Observable<String> validatePin(String pin) {
        return createValidateObservable(pin).flatMap(pwd ->
                createPin(prefs.getGuid(), new String(pwd), pin).andThen(Observable.just(pwd))
        ).compose(RxUtil.applySchedulersToObservable());
    }

    private Observable<String> createValidateObservable(String passedPin) {
        int fails = prefs.getValue(PrefsUtil.KEY_PIN_FAILS, 0);

        return pinStore.readPassword(fails, prefs.getGuid(), passedPin)
                .map(value -> {
                    try {
                        String encryptedPassword = prefs.getValue(PrefsUtil.KEY_ENCRYPTED_PASSWORD, "");
                        String password = AESUtil.decrypt(encryptedPassword,
                                value,
                                AESUtil.PIN_PBKDF2_ITERATIONS);
                        if (!restoreWavesWallet(password)) {
                            throw new RuntimeException("Failed password");
                        }
                        return password;
                    } catch (Exception e) {
                        throw Exceptions.propagate(new Throwable("Decrypt wallet failed"));
                    }
                });
    }

    private Completable createPinObservable(String walletGuid, String password, String passedPin) {
        if (passedPin == null || passedPin.equals("0000") || passedPin.length() != 4) {
            return Completable.error(new RuntimeException("Prohibited pin"));
        }

        appUtil.applyPRNGFixes();

        return Completable.create(subscriber -> {
            try {
                byte[] bytes = new byte[16];
                SecureRandom random = new SecureRandom();
                random.nextBytes(bytes);
                String value = new String(Hex.encode(bytes), "UTF-8");

                pinStore.savePasswordByKey(walletGuid, value, passedPin).subscribe(res -> {
                    String encryptedPassword = AESUtil.encrypt(
                            password.toString(), value, AESUtil.PIN_PBKDF2_ITERATIONS);
                    prefs.setValue(PrefsUtil.KEY_ENCRYPTED_PASSWORD, encryptedPassword);
                    if (!subscriber.isDisposed()) {
                        subscriber.onComplete();
                    }
                }, err -> {
                    if (!subscriber.isDisposed()) {
                        subscriber.onError(err);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "createPinObservable", e);
                if (!subscriber.isDisposed()) {
                    subscriber.onError(new RuntimeException("Failed to ecrypt password"));
                }
            }
        });
    }

    public String storeWavesWallet(String seed, String password, String walletName) {
        try {
            WavesWallet newWallet = new WavesWallet(seed.getBytes(Charsets.UTF_8));
            String walletGuid = UUID.randomUUID().toString();
            prefs.setGlobalValue(PrefsUtil.GLOBAL_LOGGED_IN_GUID, walletGuid);
            prefs.addGlobalListValue(EnvironmentManager.get().current().getName() + PrefsUtil.LIST_WALLET_GUIDS, walletGuid);
            prefs.setValue(PrefsUtil.KEY_PUB_KEY, newWallet.getPublicKeyStr());
            prefs.setValue(PrefsUtil.KEY_WALLET_NAME, walletName);
            prefs.setValue(PrefsUtil.KEY_ENCRYPTED_WALLET, newWallet.getEncryptedData(password));

            setTemporary(newWallet);


            RealmConfiguration config = new RealmConfiguration.Builder()
                    .name(String.format("%s.realm", newWallet.getPublicKeyStr()))
                    .deleteRealmIfMigrationNeeded()
                    .build();
            DBHelper.getInstance().setRealmConfig(config);

            return walletGuid;
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "storeWavesWallet: ", e);
            return null;
        }
    }

    private void setTemporary(WavesWallet newWallet) {
        if (disposable != null) {
            disposable.dispose();
        }

        wavesWallet = newWallet;
        if (!onDexScreens)
            disposable = Observable.just(1).delay(CLEAR_TIMEOUT_SECS, TimeUnit.SECONDS)
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(res -> removeWavesWallet());
    }

    public byte[] getPrivateKey() {
        if (wavesWallet != null) {
            return wavesWallet.getPrivateKey();
        }
        return null;
    }

    public String getSeedStr() {
        if (wavesWallet != null) {
            return wavesWallet.getSeedStr();
        }
        return null;
    }

    public void removeWavesWallet() {
        wavesWallet = null;
    }
}
