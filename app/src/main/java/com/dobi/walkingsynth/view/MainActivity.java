package com.dobi.walkingsynth.view;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.loader.content.CursorLoader;

import com.dobi.walkingsynth.ApplicationMvp;
import com.dobi.walkingsynth.MainApplication;
import com.dobi.walkingsynth.R;
import com.dobi.walkingsynth.model.musicgeneration.utils.Note;
import com.dobi.walkingsynth.model.musicgeneration.utils.Scale;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.io.android.AndroidFFMPEGLocator;
import be.tarsos.dsp.onsets.ComplexOnsetDetector;
import be.tarsos.dsp.onsets.OnsetHandler;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;

import static java.lang.Long.min;

/**
 * TODO: refactor to MVP
 * TODO: create view abstractions in order to separate from implementations
 * TODO: use Dagger2 a lot
 * TODO: refactor Csound and accelerometer to use RxJava
 */
public class  MainActivity extends AppCompatActivity implements ApplicationMvp.View {

    TarsosDSPAudioFormat tarsosDSPAudioFormat;
    AudioDispatcher dispatcher;
    File file;
    Uri fileUri;
    int PICKFILE_RESULT_CODE = 1;
    TextView tempoTextView2;
    TextView silenceThresholdTextView;
    TextView thresholdTextView;
    TextView onsetTextView;
    Button playButton;
    Button chooseFileButton;
    boolean isPlaying = false;
    String filename = "kick_100.wav"; // default audio track // TODO show warning when track not chosen
    String selectedFilePath;
    SeekBar silenceThresholdSlider;
    SeekBar peakThresholdSlider;
    double silenceThreshold = -70;
    double peakThreshold = 0.3; // same as default for ComplexOnsetDetector
    double songStartTime = 0;
    ArrayList<Double> beatOnsets = new ArrayList<>();
    ArrayList<Double> stepOnsets;


    String pathToMusic;
    Uri uriMusic;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MAX_STEPS_COUNT = 999;

    @BindView(R.id.steps_text_view)
    TextView stepsTextView;

    @BindView(R.id.tempo_text_view)
    TextView tempoTextView;

    @BindView(R.id.time_text_view)
    TextView timeTextView;

    @BindView(R.id.note_parameter_view)
    ParameterView notesParameterView;

    @BindView(R.id.note_text_view)
    TextView noteTextView;

    @BindView(R.id.interval_parameter_view)
    ParameterView intervalParameterView;

    @BindView(R.id.scales_parameter_view)
    ParameterView scalesParameterView;

    @BindView(R.id.graph_frame_layout)
    FrameLayout graphFrameLayout;

    @BindView(R.id.threshold_seek_bar)
    SeekBar thresholdSeekBar;

    @Inject
    GraphView accelerometerGraph;

    @Inject
    ApplicationMvp.Presenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.mainToolbar);
        setSupportActionBar(myToolbar);

        ButterKnife.bind(this);

        Locale.setDefault(Locale.ENGLISH);

        ((MainApplication) getApplication()).getApplicationComponent().inject(this);

        presenter.attachView(this);

        presenter.initialize();

        graphFrameLayout.addView(accelerometerGraph.createView(this));

        // select file to be recorded and played
        File sdCard = Environment.getExternalStorageDirectory();
        file = new File(sdCard, filename);
        Log.println(Log.DEBUG, "preload file path: ", file.getAbsolutePath()); // /storage/emulated/0/recorded_sound.wav
        /*
        filePath = file.getAbsolutePath();
        Log.e("MainActivity", "Save file path :" + filePath);
        */
        new AndroidFFMPEGLocator(this);

        // define audio format
        tarsosDSPAudioFormat = new TarsosDSPAudioFormat(TarsosDSPAudioFormat.Encoding.PCM_SIGNED,
                44100, // sample rate
                2 * 8, // sample size (in bits)
                1, // no. of channels
                2 * 1, // frame size
                44100, // frame rate
                ByteOrder.BIG_ENDIAN.equals(ByteOrder.nativeOrder()));

        onsetTextView = (TextView) findViewById(R.id.onsetTextView);
        tempoTextView2 = (TextView) findViewById(R.id.tempoTextView2);
        silenceThresholdTextView = (TextView) findViewById(R.id.silenceThresholdTextView);
        thresholdTextView = (TextView) findViewById(R.id.thresholdTextView);
        playButton = (Button) findViewById(R.id.playButton);
        chooseFileButton = (Button) findViewById(R.id.chooseFileButton);
        silenceThresholdSlider = (SeekBar) findViewById(R.id.silenceThresholdSlider);
        peakThresholdSlider = (SeekBar) findViewById(R.id.peakThresholdSlider);

        silenceThresholdTextView.setText("Silence threshold: " + silenceThreshold + "dB");
        thresholdTextView.setText("Peak threshold: " + String.format("%.1f", peakThreshold));

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlaying) {
                    playAudio();
                    isPlaying = true;
                    playButton.setText("Stop");
                } else {
                    stopPlaying();
                    isPlaying = false;
                    playButton.setText("Play");
                }
            }
        });

        silenceThresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                silenceThreshold = (double) progress;
                silenceThresholdTextView.setText("Silence threshold: " + progress + "dB");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        peakThresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                peakThreshold = (double) progress / 10;
                thresholdTextView.setText("Peak threshold: " + String.format("%.1f", peakThreshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        chooseFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                chooseFile.setType("*/*");
                chooseFile = Intent.createChooser(chooseFile, "Choose a file");
                startActivityForResult(chooseFile, PICKFILE_RESULT_CODE);
            }

        });
    }

    private void initializeThresholdSeekBar() {
        Log.d(TAG, "initializeThresholdSeekBar() to value= " + presenter.getProgressFromThreshold());
        thresholdSeekBar.setProgress(presenter.getProgressFromThreshold());

        presenter.setThresholdProgressObservable(Observable.<Integer>create(e ->
                thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    e.onNext(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        })));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_threshold:
                saveThreshold();
                return true;
            case R.id.action_save_parameters:
                saveParameters();
                return true;
            case R.id.action_info:
                // TODO present info about me
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void saveThreshold() {
        presenter.saveState();

        Toast.makeText(this, R.string.toast_threshold_saved, Toast.LENGTH_SHORT).show();
    }

    private void saveParameters() {
        presenter.saveState();

        Toast.makeText(this, getString(R.string.toast_parameters_saved) + "\n note: " + presenter.getNote() +
                "\n scale: " + presenter.getScale() + "\n interval: " + presenter.getInterval(), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        presenter.onStop();
        super.onStop();
        releaseDispatcher();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void initialize(Note note, Scale scale, int interval, int steps, int tempo, String time, Integer[] intervals) {
        noteTextView.setText(note.note);

        notesParameterView.initialize(Note.toStringArray(), note.note);
        notesParameterView.setCallback(n -> presenter.setNote(Note.getNoteByName(n)));

        scalesParameterView.initialize(Scale.toStringArray(), scale.name());
        scalesParameterView.setCallback(s -> {
            Log.d(TAG, "scalesParameterView callback: scale= " + s);
            presenter.setScale(Scale.getScaleByName(s));
        });

        stepsTextView.setText(String.valueOf(steps));

        tempoTextView.setText(String.valueOf(tempo));

        timeTextView.setText(time);

        initializeIntervals(intervals, interval);

        initializeThresholdSeekBar();
    }

    private void initializeIntervals(Integer[] intervals, int interval) {
        Observable.fromArray(intervals)
                .map(String::valueOf)
                .toList()
                .subscribe(strings -> intervalParameterView.initialize(strings.toArray(
                        new String[intervals.length]), String.valueOf(interval)))
                .dispose();

        intervalParameterView.setCallback(i -> {
            Log.d(TAG, "intervalParameterView callback: " + i);
            presenter.setInterval(Integer.valueOf(i));
        });
    }

    @Override
    public void showNote(Note note) {
        noteTextView.setText(note.note);
    }

    @Override
    public void showScale(Scale scale) {
        scalesParameterView.setValue(scale.name());
    }

    @Override
    public void showSteps(int steps) {
        stepsTextView.setText(String.valueOf(steps % MAX_STEPS_COUNT));
    }

    @Override
    public void showTempo(int tempo) {
        tempoTextView.setText(String.valueOf(tempo));
    }

    @Override
    public void showTime(String time) {
        // TODO: move  Timer to Model. Presenter would handle it.
    }

    @Override
    public TextView getTimeView() {
        return timeTextView;
    }

    public void playAudio() {
        try {
            releaseDispatcher();
            // re-read file
            File sdCard = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            file = new File(sdCard, filename);
            Log.println(Log.DEBUG, "playing file path: ", file.getAbsolutePath());
            FileInputStream fileInputStream = new FileInputStream(file);
            dispatcher = new AudioDispatcher(new UniversalAudioInputStream(fileInputStream,
                    tarsosDSPAudioFormat),
                    2048,
                    1024);

            AudioProcessor playerProcessor = new AndroidAudioPlayer(tarsosDSPAudioFormat,
                    4096,
                    0);

            dispatcher.addAudioProcessor(playerProcessor);
            // Onset detection processing
            OnsetHandler onsetHandler = new OnsetHandler() {
                double prevOnsetTime = -1;
                double prevTempo = 0;
                @Override
                public void handleOnset(double v, double v1) {
                    final double onsetTime = v; // time of onset (s) from start of audio file
                    final double onsetAbsoluteTime = songStartTime + (v * 1000);
                    final double sValue = v1; // how prominent
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.println(Log.DEBUG, "onset time: ", Double.toString(onsetTime));
                            Log.println(Log.DEBUG, "unix epoch millisec:", String.format("%.12f", onsetAbsoluteTime));
                            if(isPlaying){
                                beatOnsets.add(onsetAbsoluteTime);
                            }
                            Log.println(Log.DEBUG, "onset salience: ", Double.toString(sValue));
                            onsetTextView.setText(String.format("onset time: %.2f", onsetTime));
                            if (prevOnsetTime == -1 || prevOnsetTime >= onsetTime) { // account for looping audio
                                Log.println(Log.DEBUG, "prevOnset: ", Double.toString(prevOnsetTime));
                                prevOnsetTime = onsetTime;
                            } else if (prevOnsetTime != onsetTime) {
                                // estimate tempo
                                double currentTempo = 60/(onsetTime - prevOnsetTime);
                                double estTempo = currentTempo;
                                if (prevTempo != 0) { // take average to stabilise readings
                                    estTempo = (estTempo + prevTempo) / 2;
                                    prevTempo = estTempo;
                                } else {
                                    prevTempo = estTempo;
                                }
                                tempoTextView2.setText(String.format("%.1f", estTempo));
                                prevOnsetTime = onsetTime;
                            }
                        }
                    });
                }
            };

            double minOnsetInterval = 0.004;
            ComplexOnsetDetector complexOnsetDetector = new ComplexOnsetDetector(
                    2048, // size of FFT
                    peakThreshold,
                    minOnsetInterval,
                    silenceThreshold
            );
//            BeatRootOnsetEventHandler beatHandler = new BeatRootOnsetEventHandler();
            complexOnsetDetector.setHandler(onsetHandler);

            dispatcher.addAudioProcessor(complexOnsetDetector);
            // execute dispatcher by thread
            Thread audioThread = new Thread(dispatcher, "Audio Thread");
            songStartTime = (new Date()).getTime();
            audioThread.start();


        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void stopPlaying() {
        Log.println(Log.DEBUG, "HI", "HI");
        stepOnsets = presenter.getStepOnsets();
        for(int i = 0; i < min(stepOnsets.size(), beatOnsets.size()); i++) {
            Log.println(Log.DEBUG, "beatOnsets # " + i, String.format("%.12f", beatOnsets.get(i)));
            Log.println(Log.DEBUG, "stepOnsets # " + i, String.format("%.12f", stepOnsets.get(i)));
        }
        releaseDispatcher();
        beatOnsets.clear();
        stepOnsets.clear();
    }

    public void releaseDispatcher() {
        if (dispatcher != null) {
            if (!dispatcher.isStopped()) {
                dispatcher.stop();
            }
            dispatcher = null;
        }
    }

    // TODO implement file picker (currently doesn't return the actual path to the file when run on Samsung Galaxy Edge S7)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 1:
                if (resultCode == -1) {
                    fileUri = data.getData();
                    selectedFilePath = fileUri.getPath();
                    // select only child path
                    int cut = selectedFilePath.lastIndexOf('/');
                    if (cut != -1) {
                        selectedFilePath = selectedFilePath.substring(cut + 1);
                    }
                    // workaround for reading local files from physical device
//                    if (selectedFilePath.equals("/document/audio:437")) {
//                        filename = "kick_80.wav";
//                    } else if (selectedFilePath.equals("/document/audio:438")) {
//                        filename = "kick_100.wav";
//                    } else if (selectedFilePath.equals("/document/audio:436")) {
//                        filename = "kick_120.wav";
//                    }
                    filename = selectedFilePath;
                    Log.println(Log.DEBUG, "selected file path: ", selectedFilePath);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String getRealPathFromURI(Uri contentUri) { // code block from some stackoverflow comment --- didn't work
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(getApplicationContext(), contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

}
