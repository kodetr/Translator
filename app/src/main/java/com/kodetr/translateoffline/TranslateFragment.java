package com.kodetr.translateoffline;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAdCallback;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.kodetr.translateoffline.TranslateViewModel.Language;
import com.google.android.gms.ads.rewarded.RewardedAd;


import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import static android.app.Activity.RESULT_OK;

public class TranslateFragment extends Fragment {

    private InterstitialAd mInterstitialAd;
    private RewardedAd rewardedAd;
    private BottomSheetDialog sheetDialog;
    private TextToSpeech t1;
    private EditText srcTextView;
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 5555;

    public static TranslateFragment newInstance() {
        return new TranslateFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.translate_fragment, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@Nonnull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        iklan(view);
        textToSpeech();

        final ImageView switchButton = view.findViewById(R.id.buttonSwitchLang);
        srcTextView = view.findViewById(R.id.sourceText);
        final TextView targetTextView = view.findViewById(R.id.targetText);
        final Spinner sourceLangSelector = view.findViewById(R.id.sourceLangSelector);
        final Spinner targetLangSelector = view.findViewById(R.id.targetLangSelector);

        view.findViewById(R.id.btnAbout).setOnClickListener(view18 -> {
            clikIklanReward();
            startActivity(new Intent(getContext(), AboutActivity.class));
        });

        view.findViewById(R.id.iv_dialog).setOnClickListener(view1 -> {
            sheetDialog.show();
            clikIklanInterstitial();
        });

        view.findViewById(R.id.iv_clear).setOnClickListener(view12 -> {
            srcTextView.setText("");
            clikIklanInterstitial();
        });

        view.findViewById(R.id.iv_speech_1).setOnClickListener(view13 -> {
            t1.speak(srcTextView.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
            clikIklanInterstitial();
        });

        view.findViewById(R.id.iv_speech_2).setOnClickListener(view13 -> {
            t1.speak(targetTextView.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
            clikIklanInterstitial();
        });

        view.findViewById(R.id.iv_share).setOnClickListener(view14 -> {
            String s = targetTextView.getText().toString();
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject Here");
            sharingIntent.putExtra(Intent.EXTRA_TEXT, s);
            startActivity(Intent.createChooser(sharingIntent, "Share text via"));

            clikIklanReward();
        });
        view.findViewById(R.id.iv_copy).setOnClickListener(view15 -> {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", targetTextView.getText().toString());
            Objects.requireNonNull(clipboard).setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied Text", Toast.LENGTH_SHORT).show();

            clikIklanReward();
        });
        view.findViewById(R.id.iv_voice).setOnClickListener(view16 -> startVoiceRecognitionActivity());
        view.findViewById(R.id.iv_camera).setOnClickListener(view17 -> {
            clikIklanReward();
            Toast.makeText(getContext(), "In the development stage", Toast.LENGTH_SHORT).show();
        });

        sheetDialog = new BottomSheetDialog(getContext());
        @SuppressLint("InflateParams") View sheetView = getLayoutInflater().inflate(R.layout.dialog_item, null);
        sheetDialog.setContentView(sheetView);

        final ToggleButton sourceSyncButton = sheetView.findViewById(R.id.buttonSyncSource);
        final ToggleButton targetSyncButton = sheetView.findViewById(R.id.buttonSyncTarget);
        final TextView downloadedModelsTextView = sheetView.findViewById(R.id.downloadedModels);

        final TranslateViewModel viewModel =
                ViewModelProviders.of(this).get(TranslateViewModel.class);

        final ArrayAdapter<Language> adapter = new ArrayAdapter<>(getContext(),
                R.layout.spinner_item, viewModel.getAvailableLanguages());
        sourceLangSelector.setAdapter(adapter);
        targetLangSelector.setAdapter(adapter);
        sourceLangSelector.setSelection(adapter.getPosition(new Language("en")));
        targetLangSelector.setSelection(adapter.getPosition(new Language("id")));
        sourceLangSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setProgressText(targetTextView);
                viewModel.sourceLang.setValue(adapter.getItem(position));
                sheetDialog.show();
                clikIklanReward();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                targetTextView.setText("");
            }
        });
        targetLangSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setProgressText(targetTextView);
                viewModel.targetLang.setValue(adapter.getItem(position));
                sheetDialog.show();
                clikIklanReward();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                targetTextView.setText("");
            }
        });

        switchButton.setOnClickListener(v -> {
            setProgressText(targetTextView);
            int sourceLangPosition = sourceLangSelector.getSelectedItemPosition();
            sourceLangSelector.setSelection(targetLangSelector.getSelectedItemPosition());
            targetLangSelector.setSelection(sourceLangPosition);
            clikIklanReward();
        });

        // Set up toggle buttons to delete or download remote models locally.
        sourceSyncButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Language language = adapter.getItem(sourceLangSelector.getSelectedItemPosition());
            if (isChecked) {
                viewModel.downloadLanguage(Objects.requireNonNull(language));
            } else {
                viewModel.deleteLanguage(Objects.requireNonNull(language));
            }
        });
        targetSyncButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Language language = adapter.getItem(targetLangSelector.getSelectedItemPosition());
            if (isChecked) {
                viewModel.downloadLanguage(Objects.requireNonNull(language));
            } else {
                viewModel.deleteLanguage(Objects.requireNonNull(language));
            }
        });

        // Translate input text as it is typed
        srcTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                setProgressText(targetTextView);
                viewModel.sourceText.postValue(s.toString());
            }
        });
        viewModel.translatedText.observe(this, resultOrError -> {
            if (resultOrError.error != null) {
                Toast.makeText(getContext(), resultOrError.error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            } else {
                targetTextView.setText(resultOrError.result);
            }
        });

        // Update sync toggle button states based on downloaded models list.
        viewModel.availableModels.observe(this, firebaseTranslateRemoteModels -> {
            String output = getContext().getString(R.string.downloaded_models_label,
                    firebaseTranslateRemoteModels);
            downloadedModelsTextView.setText(output);
            sourceSyncButton.setChecked(firebaseTranslateRemoteModels.contains(
                    Objects.requireNonNull(adapter.getItem(sourceLangSelector.getSelectedItemPosition())).getCode()));
            targetSyncButton.setChecked(firebaseTranslateRemoteModels.contains(
                    Objects.requireNonNull(adapter.getItem(targetLangSelector.getSelectedItemPosition())).getCode()));
        });


    }

    private void setProgressText(TextView tv) {
        tv.setText(Objects.requireNonNull(getContext()).getString(R.string.translate_progress));
    }

    private void textToSpeech() {
        t1 = new TextToSpeech(getContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                t1.setLanguage(Locale.UK);
            }
        });
    }


    private void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.dialog_voice));
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            srcTextView.setText(Objects.requireNonNull(result).get(0));
        }
    }

    public void onPause() {
        if (t1 != null) {
            t1.stop();
            t1.shutdown();
        }
        super.onPause();
    }

    private void iklan(View view) {
        AdView mAdView = view.findViewById(R.id.adView);
        if (Connection.isNetworkConnected(getContext())) {
            mAdView.setVisibility(View.VISIBLE);
        } else {
            mAdView.setVisibility(View.GONE);
        }

        MobileAds.initialize(getContext(), initializationStatus -> {
        });

//        iklan binner
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
//        iklan interstitial
        mInterstitialAd = new InterstitialAd(getContext());
        mInterstitialAd.setAdUnitId(getString(R.string.interstisial));
        mInterstitialAd.loadAd(new AdRequest.Builder().build());
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
            }

        });
//        iklan reward
        rewardedAd = new RewardedAd(getContext(), getString(R.string.reward));
        RewardedAdLoadCallback adLoadCallback = new RewardedAdLoadCallback() {
            @Override
            public void onRewardedAdLoaded() {
                // Ad successfully loaded.
            }

            @Override
            public void onRewardedAdFailedToLoad(int errorCode) {
                // Ad failed to load.
            }
        };
        rewardedAd.loadAd(new AdRequest.Builder().build(), adLoadCallback);
    }

    private void clikIklanInterstitial() {
        if (mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
    }

    private void clikIklanReward() {
        if (rewardedAd.isLoaded()) {
            RewardedAdCallback adCallback = new RewardedAdCallback() {
                @Override
                public void onRewardedAdOpened() {
                    // Ad opened.
                }

                @Override
                public void onRewardedAdClosed() {
                    // Ad closed.
                }

                @Override
                public void onUserEarnedReward(@NonNull RewardItem reward) {
                    // User earned reward.
                }

                @Override
                public void onRewardedAdFailedToShow(int errorCode) {
                    // Ad failed to display
                }
            };
            rewardedAd.show(getActivity(), adCallback);
        } else {
            Log.d("TAG", "The rewarded ad wasn't loaded yet.");
        }
    }

}
