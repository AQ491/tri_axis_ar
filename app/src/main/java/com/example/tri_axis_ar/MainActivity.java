package com.example.tri_axis_ar;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.graphics.drawable.LevelListDrawable;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.content.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Led;

import bolts.Continuation;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private static final int N_SAMPLES = 200;
    private static List<Float> x;
    private static List<Float> y;
    private static List<Float> z;
    private TextView downstairsTextView;
    private TextView joggingTextView;
    private TextView sittingTextView;
    private TextView standingTextView;
    private TextView upstairsTextView;
    private TextView walkingTextView;
    private TextView bestAct;
    private float[] results;
    private TensorFlowClassifier classifier;

    private String[] labels = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};

    public SensorEventListener sensorL;
    public Sensor sensorS;

    private BtleService.LocalBinder serviceBinder;
    private MetaWearBoard board;
    private Accelerometer accelerometer;
    public Led trackerLED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /////////////////////////////////////////////////////////////
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
        /////////////////////////////////////////////////////////////

        x = new ArrayList<>();
        y = new ArrayList<>();
        z = new ArrayList<>();

        downstairsTextView = (TextView) findViewById(R.id.downstairs_prob);
        joggingTextView = (TextView) findViewById(R.id.jogging_prob);
        sittingTextView = (TextView) findViewById(R.id.sitting_prob);
        standingTextView = (TextView) findViewById(R.id.standing_prob);
        upstairsTextView = (TextView) findViewById(R.id.upstairs_prob);
        walkingTextView = (TextView) findViewById(R.id.walking_prob);
        bestAct = (TextView) findViewById(R.id.result_prob);

        classifier = new TensorFlowClassifier(getApplicationContext());

        //onDevice sensor initializations
        sensorS=getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorL = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                activityPrediction();
                x.add(event.values[0]);
                y.add(event.values[1]);
                z.add(event.values[2]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

    }

    @Override
    protected void onPause() {
        //uncomment for onDevice sensor
        //getSensorManager().unregisterListener(sensorL,sensorS);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //uncomment for onDevice sensor
        //getSensorManager().registerListener(sensorL, sensorS, SensorManager.SENSOR_DELAY_GAME);
    }


    public void activityPrediction() {
        if (x.size() == N_SAMPLES && y.size() == N_SAMPLES && z.size() == N_SAMPLES) {
            trackerLED.play();
            List<Float> data = new ArrayList<>();
            data.addAll(x);
            data.addAll(y);
            data.addAll(z);

            results = classifier.predictProbabilities(toFloatArray(data));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    trackerLED.stop(false);
                    downstairsTextView.setText(Float.toString(round(results[0], 2)));
                    joggingTextView.setText(Float.toString(round(results[1], 2)));
                    sittingTextView.setText(Float.toString(round(results[2], 2)));
                    standingTextView.setText(Float.toString(round(results[3], 2)));
                    upstairsTextView.setText(Float.toString(round(results[4], 2)));
                    walkingTextView.setText(Float.toString(round(results[5], 2)));

                    int maxI=0;
                    float j=0;
                    for (int i= 0;i<results.length;i++){
                        if (results[i]>j){
                            j=results[i];
                            maxI=i;
                        }
                    }
                    bestAct.setText(labels[maxI]);
                }
            });

            x.clear();
            y.clear();
            z.clear();
        }
    }

    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    private float[] toFloatArray(List<Float> list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }
    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        retrieveBoard("E1:A3:15:9B:B3:15");
    }


    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public void retrieveBoard(String macAddress) {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(macAddress);

        // Create a MetaWear board object for the Bluetooth Device
        board= serviceBinder.getMetaWearBoard(remoteDevice);
        board.connectAsync().onSuccessTask(task -> {
            Log.i("app", "Service Connected");
            trackerLED = board.getModule(Led.class);
            accelerometer = board.getModule(Accelerometer.class);
            accelerometer.configure()
                    .odr(10f)
                    .commit();
            return accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                @Override
                public void configure(RouteComponent source) {
                    source.stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                            // run classifier
                            activityPrediction();

                            //update xyz
                            x.add(data.value(Acceleration.class).x());
                            y.add(data.value(Acceleration.class).y());
                            z.add(data.value(Acceleration.class).z());
                        }
                    });
                }
            });
        }).continueWith((Continuation<Route, Void>) task -> {
            if (task.isFaulted()) {
                Log.e("app", board.isConnected() ? "Error setting up route" : "Error connecting", task.getError());
            } else {
                Log.i("app", "Connected");
            }

            return null;
        });
    }
    public void StartAccelometer(View view){
        accelerometer.acceleration().start();
        accelerometer.start();
    }
    public void StopAccelometer(View view){
        accelerometer.stop();
        accelerometer.acceleration().stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(this);
    }
}
