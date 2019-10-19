package com.kodetr.translateoffline;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateLanguage;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateModelManager;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateRemoteModel;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslator;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslatorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TranslateViewModel extends AndroidViewModel {
    private final FirebaseTranslateModelManager modelManager;
    MutableLiveData<Language> sourceLang = new MutableLiveData<>();
    MutableLiveData<Language> targetLang = new MutableLiveData<>();
    MutableLiveData<String> sourceText = new MutableLiveData<>();
    MediatorLiveData<ResultOrError> translatedText = new MediatorLiveData<>();
    MutableLiveData<List<String>> availableModels =
            new MutableLiveData<>();

    public TranslateViewModel(@Nonnull Application application) {
        super(application);
        modelManager = FirebaseTranslateModelManager.getInstance();

        // Create a translation result or error object.
        final OnCompleteListener<String> processTranslation = task -> {
            if (task.isSuccessful()) {
                translatedText.setValue(new ResultOrError(task.getResult(), null));
            } else {
                translatedText.setValue(new ResultOrError(null, task.getException()));
            }
            // Update the list of downloaded models as more may have been
            // automatically downloaded due to requested translation.
            fetchDownloadedModels();
        };

        // Start translation if any of the following change: input text, source lang, target lang.
        translatedText.addSource(sourceText, s -> translate().addOnCompleteListener(processTranslation));
        Observer<Language> languageObserver = language -> translate().addOnCompleteListener(processTranslation);
        translatedText.addSource(sourceLang, languageObserver);
        translatedText.addSource(targetLang, languageObserver);

        // Update the list of downloaded models.
        fetchDownloadedModels();
    }

    // Gets a list of all available translation languages.
    List<Language> getAvailableLanguages() {
        List<Language> languages = new ArrayList<>();
        Set<Integer> languageIds = FirebaseTranslateLanguage.getAllLanguages();
        for (Integer languageId : languageIds) {
            languages.add(new Language(FirebaseTranslateLanguage.languageCodeForLanguage(languageId)));
        }
        return languages;
    }

    private FirebaseTranslateRemoteModel getModel(Integer languageCode) {
        return new FirebaseTranslateRemoteModel.Builder(languageCode).build();
    }

    // Updates the list of downloaded models available for local translation.
    private void fetchDownloadedModels() {
        modelManager.getAvailableModels(FirebaseApp.getInstance()).addOnSuccessListener(
                remoteModels -> {
                    List<String> modelCodes = new ArrayList<>(remoteModels.size());
                    for (FirebaseTranslateRemoteModel model : remoteModels) {
                        modelCodes.add(model.getLanguageCode());
                    }
                    Collections.sort(modelCodes);
                    availableModels.setValue(modelCodes);
                });
    }

    // Starts downloading a remote model for local translation.
    void downloadLanguage(Language language) {
        FirebaseTranslateRemoteModel model =
                getModel(FirebaseTranslateLanguage.languageForLanguageCode(language.getCode()));
        modelManager.downloadRemoteModelIfNeeded(model).addOnCompleteListener(task -> fetchDownloadedModels());
    }

    // Deletes a locally stored translation model.
    void deleteLanguage(Language language) {
        FirebaseTranslateRemoteModel model =
                getModel(FirebaseTranslateLanguage.languageForLanguageCode(language.getCode()));
        modelManager.deleteDownloadedModel(model).addOnCompleteListener(task -> fetchDownloadedModels());
    }

    public Task<String> translate() {
        final TaskCompletionSource<String> translateTask = new TaskCompletionSource<String>();
        final String text = sourceText.getValue();
        final Language source = sourceLang.getValue();
        final Language target = targetLang.getValue();
        if (source == null || target == null || text == null || text.isEmpty()) {
            return Tasks.forResult("");
        }
        int sourceLangCode =
                FirebaseTranslateLanguage.languageForLanguageCode(source.getCode());
        int targetLangCode =
                FirebaseTranslateLanguage.languageForLanguageCode(target.getCode());
        FirebaseTranslatorOptions options = new FirebaseTranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build();
        final FirebaseTranslator translator =
                FirebaseNaturalLanguage.getInstance().getTranslator(options);
        return translator.downloadModelIfNeeded().continueWithTask(task -> {
            if (task.isSuccessful()) {
                return translator.translate(text);
            } else {
                Exception e = task.getException();
                if (e == null) {
                    e = new Exception(getApplication().getString(R.string.unknown_error));
                }
                return Tasks.forException(e);
            }
        });
    }

    /**
     * Holds the result of the translation or any error.
     */
    static class ResultOrError {
        final @Nullable
        String result;
        final @Nullable
        Exception error;

        ResultOrError(@Nullable String result, @Nullable Exception error) {
            this.result = result;
            this.error = error;
        }
    }

    /**
     * Holds the language code (i.e. "en") and the corresponding localized full language name
     * (i.e. "English")
     */
    static class Language implements Comparable<Language> {
        private String code;

        Language(String code) {
            this.code = code;
        }

        String getDisplayName() {
            return new Locale(code).getDisplayName();
        }

        String getCode() {
            return code;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof Language)) {
                return false;
            }

            Language otherLang = (Language) o;
            return otherLang.code.equals(code);
        }

//        @Nonnull
//        public String toString() {
//            return code + " - " + getDisplayName();
//        }

        @Nonnull
        public String toString() {
            return getDisplayName();
        }


        @Override
        public int hashCode() {
            return code.hashCode();
        }

        @Override
        public int compareTo(@Nonnull Language o) {
            return this.getDisplayName().compareTo(o.getDisplayName());
        }
    }
}
