package com.example.shakeodynka;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TranslateService {
    private final Executor executor;
    private final Translate translator;

    public TranslateService(final InputStream credentialsStream) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

        this.translator = TranslateOptions.newBuilder().setCredentials(credentials).build().getService();
        this.executor = Executors.newSingleThreadExecutor();
    }
    public interface TranslationCallback {
        void onTranslationComplete(String result);
    }

    public void translateTextAsync(final String text, final String targetLanguage, final TranslationCallback callback) {
        executor.execute(() -> {
            String translatedText = translator.translate(text, Translate.TranslateOption.targetLanguage(targetLanguage)).getTranslatedText();
            callback.onTranslationComplete(translatedText);
        });
    }
}
