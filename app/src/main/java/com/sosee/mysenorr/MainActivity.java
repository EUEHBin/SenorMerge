package com.sosee.mysenorr;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.sosee.mysenorr.sensors.Core;
import com.sosee.mysenorr.tools.Locationer;

public class MainActivity extends AppCompatActivity implements Core.onStepUpdateListener, Locationer.onLocationUpdateListener {
    private Locationer mLocationer;
    private ImageView mImageView;
    private TextView mTextView;
    private float row = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationer = new Locationer(this);
        mImageView = findViewById(R.id.iv);
        mTextView = findViewById(R.id.tv);

        Core core = new Core(this);
        //    core.reactivateSensors();
        core.enableAutocorrect();
        core.startSensors();

    }


    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();

    }


    @Override
    public void onStepUpdate(int event) {
        if (event == 0) {
            positionUpdate();
        } else {
            mLocationer.starteAutocorrect();
        }
    }

    @SuppressLint("SetTextI18n")
    private void positionUpdate() {

        float rotation = (float) Core.azimuth;

        mTextView.setText(rotation + "");

        if ((rotation > 0 && rotation < 20) && (row > 340 && row < 360)) {
            Log.d("MyTag","防止显示上多转动");
        } else if ((rotation > 340 && rotation < 360) && (row > 0 && row < 20)) {
            Log.d("MyTag","防止显示上多转动");
        } else {
            Animation rotateAnimation = new RotateAnimation(row, rotation, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setFillAfter(true);
            rotateAnimation.setDuration(50);
            rotateAnimation.setRepeatCount(0);
            rotateAnimation.setInterpolator(new LinearInterpolator());
            mImageView.startAnimation(rotateAnimation);
        }

        row = rotation;


    }

    @Override
    public void onLocationUpdate(int event) {

        switch (event) {
            case 0:
                // First Position from the Locationer

                break;
            case 5:
                //    showGPSDialog();
                break;
            case 8:
                Core.setLocation(Locationer.startLat, Locationer.startLon);

                break;
            case 12:
                // message from Locationer

                break;
            case 14:
                // next position from Locationer

        }

    }
}
