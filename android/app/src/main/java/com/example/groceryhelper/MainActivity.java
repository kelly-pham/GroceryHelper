package com.example.groceryhelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends AppCompatActivity  {
    /**
     * Handle to the Box/Can Acctivity
     */
    private Button startbutton;

    /**
     * flag for whether we want to run in diagnostic mode or not
     */
    public static boolean DIAGNOSTIC_MODE = true;

    TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Initialization
        initViewHooks(); // initialize the hooks to view screen
        initButtonListeners();

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS){
                    int result = tts.setLanguage(Locale.US);
                    if (result == tts.LANG_MISSING_DATA || result == tts.LANG_NOT_SUPPORTED){
                        Log.e("error","Language is not supported");
                    }else {
                        Log.e("success","Language Supported");
                    }
                    Log.i("TTS","Initialize Successful");
                } else{
                    Toast.makeText(getApplicationContext(),"TTS FAILED", Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    private void initViewHooks(){
        // Button Handler
        this.startbutton = (Button) findViewById(R.id.START);
    };
        /**
         * initialize on click listeners to all the buttons on screen
         */
        private void initButtonListeners() {
            // Create event handler for START button
            startbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view){
                    Intent intent = new Intent ("com.example.groceryhelper.localize.DetectorActivity");
                    startActivity(intent);
                }
            });
        };


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }



}